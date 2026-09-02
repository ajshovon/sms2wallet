package me.shovon.sms2wallet.data.repository

import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.domain.model.PushState

/**
 * Thin, [Flow]-returning wrapper over [TransactionDao] for the (future) review-queue and
 * dashboard UI.
 *
 * Holds no business rules of its own beyond what [TransactionDao] and
 * [me.shovon.sms2wallet.domain.model.PushStateTransitions] already encode - in particular it
 * never invents a new path into [PushState.QUEUED] beyond [approveForSend], and never touches
 * [PushState.SENDING]/[PushState.PUSHED], which are owned exclusively by the send pipeline.
 */
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {

    /** Rows currently needing a human's attention - see [TransactionDao.observeReviewQueue]. */
    fun observeReviewQueue(): Flow<List<TransactionEntity>> = transactionDao.observeReviewQueue()

    /** Count of transactions successfully pushed since local midnight, for the dashboard. */
    fun observePushedTodayCount(): Flow<Int> {
        val (dayStart, dayEnd) = todayBoundsMillis()
        return transactionDao.observePushedCount(dayStart, dayEnd)
    }

    /** Count of transactions still queued or in flight, for the dashboard. */
    fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()

    /** Count of transactions successfully pushed since the start of the current week, for the dashboard. */
    fun observePushedThisWeekCount(): Flow<Int> {
        val (weekStart, weekEnd) = thisWeekBoundsMillis()
        return transactionDao.observePushedCount(weekStart, weekEnd)
    }

    /** Distinct (bank, last-4) sources seen so far, for the Settings account-mapping list. */
    fun observeDistinctSources(): Flow<List<TransactionSource>> = transactionDao.observeDistinctSources()

    suspend fun findById(id: Long): TransactionEntity? = transactionDao.findById(id)

    suspend fun update(transaction: TransactionEntity) = transactionDao.update(transaction)

    /**
     * User-approves a row for sending: moves it from [PushState.PARSED] or
     * [PushState.FAILED_RETRYABLE] into [PushState.QUEUED].
     *
     * Any other current state (in particular [PushState.SENDING] or [PushState.PUSHED], which
     * only [TransactionDao.claimQueuedForSend]/reconciliation may move) is left untouched and
     * this returns `false` to signal nothing changed, rather than forcing a transition that
     * would defeat the single-send guarantee described on [PushState].
     */
    suspend fun approveForSend(id: Long): Boolean {
        val current = transactionDao.findById(id) ?: return false
        val state = runCatching { PushState.valueOf(current.pushState) }.getOrNull() ?: return false
        if (state != PushState.PARSED && state != PushState.FAILED_RETRYABLE) return false
        transactionDao.update(
            current.copy(pushState = PushState.QUEUED.name, updatedAt = System.currentTimeMillis())
        )
        return true
    }

    /**
     * Start-of-week (per the device locale's first day of week) to end-of-week, in local time.
     * Mirrors [todayBoundsMillis] so both dashboard counters use the same clock and timezone.
     */
    private fun thisWeekBoundsMillis(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val start = calendar.timeInMillis
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val end = calendar.timeInMillis - 1
        return start to end
    }

    /**
     * User-dismisses a row from the review queue: moves a reviewable row into
     * [PushState.DISMISSED] so it leaves the queue but remains on record.
     *
     * Deliberately not a delete. The row's `transaction_hash` is what makes re-scanning the SMS
     * inbox a no-op (see `TransactionDao.insertIgnore`), so deleting a dismissed transaction
     * would let the very next scan re-ingest the same message and put it straight back in the
     * queue.
     *
     * Deliberately NOT [PushState.FAILED_PERMANENT]: `TransactionDao.observeReviewQueue`
     * includes that state on purpose (a permanent send failure is something the user still has
     * to see), so dismissing into it would leave the row in the queue it was just dismissed from.
     *
     * Like [approveForSend], this refuses to touch [PushState.SENDING]/[PushState.PUSHED] and
     * returns `false` instead, so dismissing can never race the send pipeline.
     */
    suspend fun dismiss(id: Long): Boolean {
        val current = transactionDao.findById(id) ?: return false
        val state = runCatching { PushState.valueOf(current.pushState) }.getOrNull() ?: return false
        if (state != PushState.PARSED &&
            state != PushState.FAILED_RETRYABLE &&
            state != PushState.FAILED_PERMANENT &&
            state != PushState.NEEDS_VERIFY
        ) {
            return false
        }
        transactionDao.update(
            current.copy(
                pushState = PushState.DISMISSED.name,
                lastError = DISMISSED_BY_USER,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /**
     * Stores a hand-entered transaction (cash spending that never produced an SMS) directly in
     * [PushState.QUEUED], since the user has already reviewed it by typing it.
     *
     * A manual entry has no source SMS and therefore no natural `transaction_hash`, so a random
     * one is generated. That is correct rather than a shortcut: the hash exists to make
     * re-parsing the *same message* idempotent, and there is no message here to re-parse. Two
     * identical manual entries are two real transactions the user genuinely entered twice.
     */
    suspend fun insertManual(
        amount: BigDecimal,
        isIncome: Boolean,
        merchant: String?,
        note: String?,
        walletAccountId: String,
        walletCategoryId: String?,
        currency: String = DEFAULT_CURRENCY,
        timestamp: Long = System.currentTimeMillis(),
    ): Long {
        val entity = TransactionEntity(
            transactionHash = "$MANUAL_HASH_PREFIX${UUID.randomUUID()}",
            bankName = MANUAL_SOURCE_NAME,
            accountLast4 = null,
            amount = amount.toPlainString(),
            type = if (isIncome) TransactionType.INCOME.name else TransactionType.EXPENSE.name,
            merchant = merchant,
            reference = note,
            currency = currency,
            smsSender = MANUAL_SOURCE_NAME,
            smsBody = "",
            timestamp = timestamp,
            pushState = PushState.QUEUED.name,
            walletRecordId = null,
            walletAccountId = walletAccountId,
            walletCategoryId = walletCategoryId,
            lastError = null,
            attemptCount = 0,
            suspectedDuplicateOfId = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        return transactionDao.insertIgnore(entity)
    }

    private fun todayBoundsMillis(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis - 1
        return start to end
    }

    private companion object {
        /** Recorded in `last_error` as a human-readable note on why the row left the queue. */
        const val DISMISSED_BY_USER = "Dismissed by user"

        /** `bank_name`/`sms_sender` marker for rows the user typed rather than any parser produced. */
        const val MANUAL_SOURCE_NAME = "Manual entry"

        /** Namespaces generated hashes so a manual row can never collide with a real SMS hash. */
        const val MANUAL_HASH_PREFIX = "manual:"

        const val DEFAULT_CURRENCY = "BDT"
    }
}
