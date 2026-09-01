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
}
