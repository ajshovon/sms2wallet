package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.UnmatchedSmsEntity

@Dao
interface UnmatchedSmsDao {

    /** Ignores the insert when [UnmatchedSmsEntity.smsHash] already exists, so rescans don't duplicate rows. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sms: UnmatchedSmsEntity): Long

    @Delete
    suspend fun delete(sms: UnmatchedSmsEntity)

    @Query("SELECT * FROM unmatched_sms ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<UnmatchedSmsEntity>>

    @Query("DELETE FROM unmatched_sms WHERE id = :id")
    suspend fun deleteById(id: Long)
}
