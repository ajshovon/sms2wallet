package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.PushLogEntity

@Dao
interface PushLogDao {

    @Insert
    suspend fun insert(log: PushLogEntity): Long

    @Query("SELECT * FROM push_log WHERE transaction_id = :transactionId ORDER BY created_at DESC")
    fun observeForTransaction(transactionId: Long): Flow<List<PushLogEntity>>

    @Query("SELECT * FROM push_log ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PushLogEntity>>

    /**
     * Recent push attempts joined to their transaction, for the Activity tab. LEFT JOIN so a
     * log row survives (and still renders) after its transaction row is gone.
     */
    @Query(
        """
        SELECT p.id AS id,
               p.transaction_id AS transaction_id,
               p.success AS success,
               p.message AS message,
               p.created_at AS created_at,
               t.merchant AS merchant,
               t.amount AS amount,
               t.type AS type,
               t.push_state AS push_state
        FROM push_log p
        LEFT JOIN transactions t ON t.id = p.transaction_id
        ORDER BY p.created_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithTransaction(limit: Int): Flow<List<PushLogWithTransaction>>
}
