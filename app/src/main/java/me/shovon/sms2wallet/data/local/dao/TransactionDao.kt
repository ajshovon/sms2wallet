package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.domain.model.PushState

@Dao
interface TransactionDao {

    /**
     * Inserts a freshly-parsed transaction. Relies on the unique index on `transaction_hash`
     * (see [TransactionEntity]) plus [OnConflictStrategy.IGNORE] to silently drop rows that
     * were already stored - this is the app's L1 dedup, and it is how re-parsing the same SMS
     * (e.g. on a rescan) is made a no-op instead of creating a duplicate transaction.
     *
     * @return the new row id, or **-1** if a row with the same `transaction_hash` already
     *   existed and the insert was ignored. Callers must check for -1 rather than assuming the
     *   insert always succeeds.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE push_state = :state ORDER BY timestamp DESC")
    fun observeByState(state: PushState): Flow<List<TransactionEntity>>

    /**
     * Everything that currently needs a human's attention: not-yet-approved [PushState.PARSED]
     * rows, both flavours of failure, and anything stuck in [PushState.NEEDS_VERIFY] pending
     * reconciliation. Newest first so the most recent activity surfaces at the top.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE push_state IN (:parsed, :failedRetryable, :failedPermanent, :needsVerify)
        ORDER BY timestamp DESC
        """
    )
    fun observeReviewQueue(
        parsed: PushState = PushState.PARSED,
        failedRetryable: PushState = PushState.FAILED_RETRYABLE,
        failedPermanent: PushState = PushState.FAILED_PERMANENT,
        needsVerify: PushState = PushState.NEEDS_VERIFY
    ): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE push_state = :queued ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun peekQueuedOldestFirst(limit: Int, queued: PushState = PushState.QUEUED): List<TransactionEntity>

    @Query(
        """
        UPDATE transactions
        SET push_state = :sending, attempt_count = attempt_count + 1, updated_at = :now
        WHERE id IN (:ids) AND push_state = :queued
        """
    )
    suspend fun markSending(
        ids: List<Long>,
        now: Long,
        sending: PushState = PushState.SENDING,
        queued: PushState = PushState.QUEUED
    ): Int

    /**
     * Re-reads the rows that this claim actually flipped to `SENDING`, identified by the
     * `updated_at` stamp written by [markSending]. Rows lost to a concurrent claim carry a
     * different stamp and are excluded.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE id IN (:ids) AND push_state = :sending AND updated_at = :now
        ORDER BY timestamp ASC
        """
    )
    suspend fun findSendingByIds(
        ids: List<Long>,
        now: Long,
        sending: PushState = PushState.SENDING
    ): List<TransactionEntity>

    /**
     * Atomically claims up to [limit] [PushState.QUEUED] rows (oldest first, i.e. FIFO) for
     * sending: they are flipped to [PushState.SENDING] **inside this same transaction, before
     * the caller makes any HTTP request**, then returned.
     *
     * This ordering is the whole point. Because `SENDING` is committed durably before the
     * network call happens, a process death, crash, or force-stop between the claim and the
     * HTTP response leaves the row sitting in `SENDING`, never in `QUEUED` - so it is
     * structurally impossible for a second `claimQueuedForSend` call (e.g. after the app
     * restarts) to pick the same row up again and send it twice. A row that is genuinely stuck
     * in `SENDING` (e.g. the app was killed mid-request) is only ever recovered later by
     * [findOrphanedSending], which requires an explicit reconciliation decision rather than a
     * blind resend.
     */
    @Transaction
    suspend fun claimQueuedForSend(limit: Int): List<TransactionEntity> {
        val candidates = peekQueuedOldestFirst(limit)
        if (candidates.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val ids = candidates.map { it.id }
        // The UPDATE is guarded on push_state = QUEUED, so if a concurrent claim won the race
        // for some of these rows the guard silently skips them and the row count comes back
        // short. Re-read the rows we actually own rather than trusting the pre-update snapshot:
        // returning a row we did not claim would let two senders POST the same transaction.
        val claimed = markSending(ids, now)
        if (claimed == 0) return emptyList()
        return findSendingByIds(ids, now)
    }

    @Query(
        """
        UPDATE transactions
        SET push_state = :pushed, wallet_record_id = :walletRecordId, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun markPushed(
        id: Long,
        walletRecordId: String,
        now: Long = System.currentTimeMillis(),
        pushed: PushState = PushState.PUSHED
    )

    @Query(
        """
        UPDATE transactions
        SET push_state = :state, last_error = :error, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun updateFailedState(id: Long, state: PushState, error: String?, now: Long)

    /**
     * Moves a row out of [PushState.SENDING] into one of the non-terminal failure states.
     * [state] must be [PushState.FAILED_RETRYABLE], [PushState.FAILED_PERMANENT], or
     * [PushState.NEEDS_VERIFY] - passing anything else (in particular [PushState.QUEUED] or
     * [PushState.PUSHED]) would defeat the single-send guarantee and is rejected.
     */
    @Transaction
    suspend fun markFailed(id: Long, state: PushState, error: String?) {
        require(
            state == PushState.FAILED_RETRYABLE ||
                state == PushState.FAILED_PERMANENT ||
                state == PushState.NEEDS_VERIFY
        ) { "markFailed must target a failure/ambiguous state, got $state" }
        updateFailedState(id, state, error, System.currentTimeMillis())
    }

    /**
     * Rows that have been sitting in [PushState.SENDING] since before [olderThanMillis] (an
     * absolute epoch-millis cutoff, not a duration) - candidates for reconciliation because the
     * app most likely died or lost connectivity mid-request. Callers must resolve these via
     * [findNeedsVerify]-style server lookups (or move them to [PushState.NEEDS_VERIFY]), never
     * by requeuing them directly.
     */
    @Query("SELECT * FROM transactions WHERE push_state = :sending AND updated_at < :olderThanMillis")
    suspend fun findOrphanedSending(
        olderThanMillis: Long,
        sending: PushState = PushState.SENDING
    ): List<TransactionEntity>

    /** Rows awaiting reconciliation against the Wallet API before any further action is taken. */
    @Query("SELECT * FROM transactions WHERE push_state = :needsVerify")
    suspend fun findNeedsVerify(needsVerify: PushState = PushState.NEEDS_VERIFY): List<TransactionEntity>

    /**
     * Candidate cross-provider duplicates of [excludeId]: other locally-known transactions
     * against the same Wallet account, with the exact same (string-normalized) amount, whose
     * timestamp falls within [fromTs]..[toTs]. Used to catch the case where the same
     * real-world payment is reported by more than one SMS source (e.g. a bank debit SMS and a
     * separate merchant/MFS confirmation), which the transaction_hash-based L1 dedup cannot
     * catch because the two SMS bodies differ.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE wallet_account_id = :walletAccountId
          AND amount = :amount
          AND timestamp BETWEEN :fromTs AND :toTs
          AND id != :excludeId
        """
    )
    suspend fun findPotentialDuplicate(
        walletAccountId: String,
        amount: String,
        fromTs: Long,
        toTs: Long,
        excludeId: Long
    ): List<TransactionEntity>

    /** Count of transactions successfully pushed within [dayStartMillis]..[dayEndMillis], for the dashboard. */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE push_state = :pushed AND updated_at BETWEEN :dayStartMillis AND :dayEndMillis"
    )
    fun observePushedCount(
        dayStartMillis: Long,
        dayEndMillis: Long,
        pushed: PushState = PushState.PUSHED
    ): Flow<Int>

    /** Count of transactions still waiting to be sent (queued or currently in flight), for the dashboard. */
    @Query("SELECT COUNT(*) FROM transactions WHERE push_state IN (:queued, :sending)")
    fun observePendingCount(
        queued: PushState = PushState.QUEUED,
        sending: PushState = PushState.SENDING
    ): Flow<Int>
}
