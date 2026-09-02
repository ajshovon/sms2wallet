package me.shovon.sms2wallet.data.push

import java.math.BigDecimal
import java.time.Instant
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.entity.PushLogEntity
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.WalletApiClient
import me.shovon.sms2wallet.data.remote.dto.CreateRecordRequest
import me.shovon.sms2wallet.data.remote.dto.RecordAmount
import me.shovon.sms2wallet.data.remote.dto.RecordState

/**
 * Sends approved transactions to the Wallet API.
 *
 * This is the piece the app was missing: `TransactionDao.claimQueuedForSend` and
 * `WalletApiClient.createRecords` both existed, but nothing joined them, so approving a
 * transaction moved it to [me.shovon.sms2wallet.domain.model.PushState.QUEUED] and it stopped
 * there forever.
 *
 * Deliberately a plain injectable class rather than logic inside the Worker, so the whole
 * send-and-apply-results path can be unit tested against a Ktor `MockEngine` without
 * WorkManager.
 *
 * ## How the single-send guarantee is preserved
 *
 * `claimQueuedForSend` commits rows to `SENDING` *before* any HTTP call, so a crash mid-request
 * leaves them in `SENDING`, never `QUEUED`, and no second pass can pick them up. This class only
 * ever returns a row to `QUEUED` via [TransactionDao.requeueUnsent], and only for outcomes where
 * the request provably never reached the server. Anything genuinely ambiguous goes to
 * `NEEDS_VERIFY` for reconciliation instead.
 */
class TransactionSender(
    private val transactionDao: TransactionDao,
    private val pushLogDao: PushLogDao,
    private val walletApiClient: WalletApiClient,
    /**
     * Whether a Wallet token is configured, read lazily on every pass - mirroring
     * `KtorWalletApiClient.tokenProvider`, since the user can add or clear it at any time.
     * A provider rather than the store itself also keeps this class free of Keystore/DataStore
     * types, so the whole send path is unit-testable.
     */
    private val hasToken: suspend () -> Boolean,
) {

    /** What the caller (the Worker) should do next. */
    sealed interface Outcome {
        /** Nothing to send, or everything resolved. No retry needed. */
        data object Done : Outcome
        /** Nothing was sent because no token is configured; rows were left untouched in QUEUED. */
        data object NoToken : Outcome
        /** A transient condition (offline, rate limited, server still syncing). Retry with backoff. */
        data class Retry(val reason: String) : Outcome
    }

    /**
     * Claims a batch of queued transactions and pushes them.
     *
     * @return whether the caller should schedule a retry.
     */
    suspend fun sendQueued(): Outcome {
        // Check for a token BEFORE claiming: claiming moves rows to SENDING, and a row parked in
        // SENDING with no token would need reconciliation to get out, for a request that was
        // never even attempted.
        if (!hasToken()) return Outcome.NoToken

        val claimed = transactionDao.claimQueuedForSend(BATCH_SIZE)
        if (claimed.isEmpty()) return Outcome.Done

        // Rows the API would reject anyway are failed locally rather than spending one of the
        // 300 requests/hour on a response we can already predict.
        val (sendable, unsendable) = claimed.partition { it.isSendable() }
        unsendable.forEach { row ->
            failPermanently(row, row.missingFieldReason())
        }
        if (sendable.isEmpty()) return Outcome.Done

        val requests = sendable.map { it.toCreateRecordRequest() }
        return when (val result = walletApiClient.createRecords(requests)) {
            is ApiResult.Success -> {
                applyBatchResults(sendable, result.data)
                Outcome.Done
            }

            // Provably never processed: safe to return to QUEUED and try again later.
            is ApiResult.RateLimited -> {
                requeueAll(sendable, "Rate limited by Wallet; will retry")
                Outcome.Retry("rate limited")
            }
            is ApiResult.SyncInProgress -> {
                requeueAll(sendable, "Wallet is still doing its first sync; will retry")
                Outcome.Retry("sync in progress")
            }
            is ApiResult.NetworkError -> if (result.ambiguous) {
                // The request may already have been processed. Never retry blindly - a duplicate
                // record is exactly what this app exists to prevent.
                sendable.forEach { row ->
                    markNeedsVerify(row, "Connection lost mid-request: verifying before retrying")
                }
                Outcome.Done
            } else {
                requeueAll(sendable, "No connection; will retry")
                Outcome.Retry("offline")
            }

            // Fixable by the user, but not by retrying on a timer.
            is ApiResult.Unauthorized -> {
                failAll(sendable, "Wallet rejected the API token. Update it in Settings, then retry.")
                Outcome.Done
            }
            is ApiResult.InvalidRequest -> {
                failAll(sendable, result.message)
                Outcome.Done
            }
            is ApiResult.HttpError -> if (result.status in 500..599) {
                requeueAll(sendable, "Wallet server error ${result.status}; will retry")
                Outcome.Retry("server error ${result.status}")
            } else {
                failAll(sendable, "Wallet rejected this (${result.status}): ${result.message.orEmpty()}")
                Outcome.Done
            }
        }
    }

    /** True while anything is still waiting to be sent, so a worker knows to keep draining. */
    suspend fun hasQueuedWork(): Boolean = transactionDao.peekQueuedOldestFirst(1).isNotEmpty()

    /**
     * Applies a batch response per item.
     *
     * Batch writes are not atomic and the results are keyed by `inputIndex`, not by position, so
     * each row is matched by its own index. A row with **no** result is the dangerous case: the
     * server may or may not have written it, so it goes to `NEEDS_VERIFY` rather than being
     * assumed failed and retried.
     */
    private suspend fun applyBatchResults(
        sent: List<TransactionEntity>,
        response: me.shovon.sms2wallet.data.remote.dto.CreateRecordsResponse,
    ) {
        val byIndex = response.results.associateBy { it.inputIndex }
        sent.forEachIndexed { index, row ->
            val result = byIndex[index]
            when {
                result == null -> markNeedsVerify(row, "Wallet did not report an outcome for this record")

                result.success -> {
                    val recordId = result.id ?: result.record?.id
                    if (recordId == null) {
                        // Reported as created but with no id to store: we cannot prove which
                        // record it is, so treat it as needing verification rather than pushed.
                        markNeedsVerify(row, "Wallet reported success without a record id")
                    } else {
                        transactionDao.markPushed(id = row.id, walletRecordId = recordId)
                        log(row, "CREATE_RECORD", success = true, message = null)
                    }
                }

                // A client error is a fact about the payload; retrying it unchanged is pointless.
                result.errorType == CLIENT_ERROR -> failPermanently(row, result.errorMessage())
                else -> failRetryable(row, result.errorMessage())
            }
        }
    }

    // ---- State transitions + audit log --------------------------------------

    private suspend fun requeueAll(rows: List<TransactionEntity>, reason: String) {
        rows.forEach { row ->
            if (row.attemptCount >= MAX_AUTO_ATTEMPTS) {
                // Stop the automatic loop and hand it to the user, rather than retrying forever.
                failRetryable(row, "Gave up after ${row.attemptCount} attempts: $reason")
            } else {
                transactionDao.requeueUnsent(id = row.id, error = reason, now = System.currentTimeMillis())
                log(row, "CREATE_RECORD", success = false, message = reason)
            }
        }
    }

    private suspend fun failAll(rows: List<TransactionEntity>, reason: String) =
        rows.forEach { failPermanently(it, reason) }

    private suspend fun failPermanently(row: TransactionEntity, reason: String) {
        transactionDao.markFailed(row.id, me.shovon.sms2wallet.domain.model.PushState.FAILED_PERMANENT, reason)
        log(row, "CREATE_RECORD", success = false, message = reason)
    }

    private suspend fun failRetryable(row: TransactionEntity, reason: String) {
        transactionDao.markFailed(row.id, me.shovon.sms2wallet.domain.model.PushState.FAILED_RETRYABLE, reason)
        log(row, "CREATE_RECORD", success = false, message = reason)
    }

    private suspend fun markNeedsVerify(row: TransactionEntity, reason: String) {
        transactionDao.markFailed(row.id, me.shovon.sms2wallet.domain.model.PushState.NEEDS_VERIFY, reason)
        log(row, "CREATE_RECORD", success = false, message = reason)
    }

    /** Writes the audit row the Activity tab reads. Never records SMS content, only the outcome. */
    private suspend fun log(row: TransactionEntity, operation: String, success: Boolean, message: String?) {
        pushLogDao.insert(
            PushLogEntity(
                transactionId = row.id,
                operation = operation,
                httpStatus = null,
                success = success,
                message = message,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private companion object {
        /** The API caps batch writes at 10; one claim maps to exactly one request. */
        const val BATCH_SIZE = 10

        /** After this many automatic attempts a row stops auto-retrying and waits for the user. */
        const val MAX_AUTO_ATTEMPTS = 5

        const val CLIENT_ERROR = "client_error"
    }
}

// ---- Entity -> request mapping ---------------------------------------------

/**
 * A record needs a destination account and a non-zero amount before it can be sent.
 *
 * A category is deliberately NOT required: the OpenAPI schema lists only `accountId`, `amount`
 * and `recordDate` as required on `CreateRecordRequest`, so refusing to send an uncategorised
 * transaction would reject rows the server would happily accept (Wallet files them under its
 * own default).
 */
private fun TransactionEntity.isSendable(): Boolean =
    !walletAccountId.isNullOrBlank() && amountOrNull() != null

private fun TransactionEntity.missingFieldReason(): String = when {
    walletAccountId.isNullOrBlank() -> "No Wallet account chosen for this transaction."
    else -> "Amount '$amount' is not a valid number."
}

private fun TransactionEntity.amountOrNull(): BigDecimal? =
    runCatching { BigDecimal(amount) }.getOrNull()?.takeIf { it.signum() != 0 }

private fun me.shovon.sms2wallet.data.remote.dto.RecordResultDto.errorMessage(): String =
    error ?: "Wallet rejected this record"

/**
 * Maps a stored transaction onto the API's create-record shape.
 *
 * The Wallet API has no income/expense field: the **sign of the amount** carries it, so an
 * expense is sent negative. Text fields are truncated to the API's 255-character maximum here,
 * because `CreateRecordRequest` validates that in an `init` block and would otherwise throw.
 */
private fun TransactionEntity.toCreateRecordRequest(): CreateRecordRequest {
    val magnitude = BigDecimal(amount).abs()
    val isIncome = type == TransactionType.INCOME.name
    val signed = if (isIncome) magnitude else magnitude.negate()

    return CreateRecordRequest(
        accountId = walletAccountId!!,
        amount = RecordAmount(value = signed.toDouble(), currencyCode = currency.takeIf { it.isNotBlank() }),
        recordDate = Instant.ofEpochMilli(timestamp).toString(),
        categoryId = walletCategoryId,
        counterParty = merchant?.takeIf { it.isNotBlank() }?.take(MAX_TEXT_FIELD),
        note = reference?.takeIf { it.isNotBlank() }?.take(MAX_TEXT_FIELD),
        recordState = RecordState.CLEARED,
    )
}

/** The API's documented cap for `counterParty` and `note`. */
private const val MAX_TEXT_FIELD = 255
