package me.shovon.sms2wallet.push

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.PushLogWithTransaction
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.sms2wallet.data.local.entity.PushLogEntity
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.domain.model.PushState

/**
 * In-memory [TransactionDao] seeded with an already-claimed batch.
 *
 * `claimQueuedForSend` is overridden to hand back the seeded rows so each test controls exactly
 * what the sender is given; every state-transition method below is real, so the tests exercise
 * the same guards production uses (notably `markFailed`'s require, inherited from the interface).
 */
class FakeTransactionDao(claimed: List<TransactionEntity>) : TransactionDao {

    val rows: MutableMap<Long, TransactionEntity> =
        claimed.associateBy { it.id }.toMutableMap()

    var claimCallCount: Int = 0
        private set

    private val claimable = claimed

    override suspend fun claimQueuedForSend(limit: Int): List<TransactionEntity> {
        claimCallCount++
        return claimable.take(limit)
    }

    override suspend fun markPushed(id: Long, walletRecordId: String, now: Long, pushed: PushState) {
        rows[id]?.let { rows[id] = it.copy(pushState = pushed.name, walletRecordId = walletRecordId, updatedAt = now) }
    }

    override suspend fun updateFailedState(id: Long, state: PushState, error: String?, now: Long) {
        rows[id]?.let { rows[id] = it.copy(pushState = state.name, lastError = error, updatedAt = now) }
    }

    override suspend fun requeueUnsent(
        id: Long,
        error: String?,
        now: Long,
        queued: PushState,
        sending: PushState,
    ): Int {
        val row = rows[id] ?: return 0
        if (row.pushState != sending.name) return 0
        rows[id] = row.copy(pushState = queued.name, lastError = error, updatedAt = now)
        return 1
    }

    override suspend fun peekQueuedOldestFirst(limit: Int, queued: PushState): List<TransactionEntity> =
        rows.values.filter { it.pushState == queued.name }.take(limit)

    // ---- Unused by the sender ------------------------------------------------

    override suspend fun insertIgnore(transaction: TransactionEntity): Long = 0
    override suspend fun findById(id: Long): TransactionEntity? = rows[id]
    override suspend fun update(transaction: TransactionEntity) { rows[transaction.id] = transaction }
    override fun observeByState(state: PushState): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override fun observeReviewQueue(
        parsed: PushState,
        failedRetryable: PushState,
        failedPermanent: PushState,
        needsVerify: PushState,
    ): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override fun observeReviewQueueCount(
        parsed: PushState,
        failedRetryable: PushState,
        failedPermanent: PushState,
        needsVerify: PushState,
    ): Flow<Int> = flowOf(rows.values.count {
        it.pushState in setOf(parsed.name, failedRetryable.name, failedPermanent.name, needsVerify.name)
    })

    override suspend fun updateCategoryIfReviewable(
        id: Long,
        categoryId: String,
        now: Long,
        parsed: PushState,
        failedRetryable: PushState,
        failedPermanent: PushState,
        needsVerify: PushState,
    ): Int {
        val row = rows[id] ?: return 0
        val reviewable = setOf(parsed.name, failedRetryable.name, failedPermanent.name, needsVerify.name)
        if (row.pushState !in reviewable) return 0
        rows[id] = row.copy(walletCategoryId = categoryId, updatedAt = now)
        return 1
    }
    override suspend fun markSending(ids: List<Long>, now: Long, sending: PushState, queued: PushState): Int = 0
    override suspend fun findSendingByIds(ids: List<Long>, now: Long, sending: PushState): List<TransactionEntity> =
        emptyList()
    override suspend fun findOrphanedSending(olderThanMillis: Long, sending: PushState): List<TransactionEntity> =
        rows.values.filter { it.pushState == sending.name && it.updatedAt < olderThanMillis }
    override suspend fun findNeedsVerify(needsVerify: PushState): List<TransactionEntity> =
        rows.values.filter { it.pushState == needsVerify.name }
    override suspend fun findPotentialDuplicate(
        walletAccountId: String,
        amount: String,
        fromTs: Long,
        toTs: Long,
        excludeId: Long,
    ): List<TransactionEntity> = emptyList()
    override fun observePushedCount(dayStartMillis: Long, dayEndMillis: Long, pushed: PushState): Flow<Int> = flowOf(0)
    override fun observePendingCount(queued: PushState, sending: PushState): Flow<Int> = flowOf(0)
    override fun observeDistinctSources(): Flow<List<TransactionSource>> = flowOf(emptyList())
    override suspend fun dismissAllReviewable(
        reason: String,
        now: Long,
        dismissed: PushState,
        parsed: PushState,
        failedRetryable: PushState,
        failedPermanent: PushState,
        needsVerify: PushState,
    ): Int = 0

    override suspend fun findClaimedWalletRecordIdsByOthers(recordIds: List<String>, excludeId: Long): List<String> =
        rows.values.filter { it.id != excludeId && it.walletRecordId in recordIds }.mapNotNull { it.walletRecordId }

    override suspend fun findByWalletRecordId(walletRecordId: String): TransactionEntity? =
        rows.values.firstOrNull { it.walletRecordId == walletRecordId }
}

/** Captures the audit rows the Activity tab reads. */
class FakePushLogDao : PushLogDao {
    val logs = mutableListOf<PushLogEntity>()
    override suspend fun insert(log: PushLogEntity): Long {
        logs += log
        return logs.size.toLong()
    }
    override fun observeForTransaction(transactionId: Long): Flow<List<PushLogEntity>> = flowOf(emptyList())
    override fun observeRecent(limit: Int): Flow<List<PushLogEntity>> = flowOf(emptyList())
    override fun observeRecentWithTransaction(limit: Int): Flow<List<PushLogWithTransaction>> = flowOf(emptyList())
}
