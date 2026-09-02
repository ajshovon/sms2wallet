package me.shovon.sms2wallet.data.push

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.entity.PushLogEntity
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.WalletApiClient
import me.shovon.sms2wallet.domain.model.PushState

/**
 * Resolves transactions whose send outcome is unknown, by asking the server what it actually
 * holds rather than guessing.
 *
 * Two ways a row gets here:
 *  - the send timed out after the request was written ([PushState.NEEDS_VERIFY]);
 *  - the process died between the claim and the response, leaving the row stranded in
 *    [PushState.SENDING].
 *
 * Both mean "the server may or may not have created this record". Retrying blindly could
 * duplicate it and the Wallet API has no idempotency key to protect against that, so the only
 * safe move is a lookup: if a matching record exists we adopt its id, and only when the server
 * demonstrably has nothing do we put the row back in the queue.
 *
 * Without this, every ambiguous failure would strand a transaction permanently.
 */
class TransactionReconciler(
    private val transactionDao: TransactionDao,
    private val pushLogDao: PushLogDao,
    private val walletApiClient: WalletApiClient,
) {

    /**
     * Reconciles everything currently in an unknown state.
     *
     * @return true if any row was resolved back into the send queue, so the caller knows to run
     *   another send pass.
     */
    suspend fun reconcile(now: Long = System.currentTimeMillis()): Boolean {
        val stranded = transactionDao.findOrphanedSending(now - ORPHAN_AFTER_MILLIS)
        val ambiguous = transactionDao.findNeedsVerify()

        var requeuedAny = false
        (stranded + ambiguous).distinctBy { it.id }.forEach { row ->
            if (resolve(row)) requeuedAny = true
        }
        return requeuedAny
    }

    /** @return true if the row was returned to the queue (i.e. the server had nothing). */
    private suspend fun resolve(row: TransactionEntity): Boolean {
        val accountId = row.walletAccountId
        if (accountId.isNullOrBlank()) {
            // Nothing to query against: it could never have been sent in the first place.
            markFailed(row, "Cannot verify: no Wallet account on this transaction")
            return false
        }

        return when (val result = walletApiClient.findRecords(
            accountId = accountId,
            dayIso = row.recordDayIso(),
            amount = row.signedAmountString(),
        )) {
            is ApiResult.Success -> {
                val match = result.data.firstOrNull()
                if (match?.id != null) {
                    // The server does hold it: adopt the record instead of sending again.
                    transactionDao.markPushed(id = row.id, walletRecordId = match.id!!)
                    log(row, success = true, "Reconciled: Wallet already had this record")
                    false
                } else {
                    // Proven absent, so re-queueing cannot duplicate anything.
                    transactionDao.requeueUnsent(
                        id = row.id,
                        error = "Verified as not sent; queued again",
                        now = System.currentTimeMillis(),
                    ).let { changed ->
                        if (changed == 0) {
                            // Not in SENDING (it was NEEDS_VERIFY), so move it explicitly.
                            transactionDao.update(
                                row.copy(
                                    pushState = PushState.QUEUED.name,
                                    lastError = "Verified as not sent; queued again",
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    log(row, success = false, "Verified as not sent; queued again")
                    true
                }
            }

            // Cannot verify right now. Leave the row exactly as it is and try again next run -
            // guessing either way here is what creates duplicates.
            else -> false
        }
    }

    private suspend fun markFailed(row: TransactionEntity, reason: String) {
        transactionDao.markFailed(row.id, PushState.FAILED_PERMANENT, reason)
        log(row, success = false, reason)
    }

    private suspend fun log(row: TransactionEntity, success: Boolean, message: String) {
        pushLogDao.insert(
            PushLogEntity(
                transactionId = row.id,
                operation = "VERIFY_RECORD",
                httpStatus = null,
                success = success,
                message = message,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private companion object {
        /**
         * How long a row may sit in `SENDING` before it is treated as stranded. Long enough that
         * a slow-but-live request is never yanked out from under itself.
         */
        const val ORPHAN_AFTER_MILLIS = 5 * 60 * 1000L
    }
}

/** `eq.`-filterable day, matching how the record was sent. */
private fun TransactionEntity.recordDayIso(): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC))

/** The amount as it would have been sent: negative for an expense, positive for income. */
private fun TransactionEntity.signedAmountString(): String {
    val magnitude = runCatching { BigDecimal(amount).abs() }.getOrDefault(BigDecimal.ZERO)
    val signed = if (type == TransactionType.INCOME.name) magnitude else magnitude.negate()
    return signed.toPlainString()
}
