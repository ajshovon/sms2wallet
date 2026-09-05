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

    @Query(
        """
        SELECT * FROM unmatched_sms
        WHERE id IN (
            SELECT MAX(id) FROM unmatched_sms
            GROUP BY sender, body
        )
        ORDER BY timestamp DESC
        """
    )
    fun observeAll(): Flow<List<UnmatchedSmsEntity>>

    @Query(
        """
        DELETE FROM unmatched_sms
        WHERE id = :id OR (
            sender = (SELECT sender FROM unmatched_sms WHERE id = :id) AND
            body = (SELECT body FROM unmatched_sms WHERE id = :id)
        )
        """
    )
    suspend fun deleteById(id: Long)

    @Query(
        """
        DELETE FROM unmatched_sms
        WHERE id NOT IN (
            SELECT MAX(id) FROM unmatched_sms
            GROUP BY sender, body
        )
        """
    )
    suspend fun deleteDuplicates()
}
