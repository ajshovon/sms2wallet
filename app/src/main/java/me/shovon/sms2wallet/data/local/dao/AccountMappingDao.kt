package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.AccountMappingEntity

@Dao
interface AccountMappingDao {

    /** Upserts by the (bank_name, account_last4) unique index - editing a mapping replaces it in place. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: AccountMappingEntity): Long

    @Update
    suspend fun update(mapping: AccountMappingEntity)

    @Delete
    suspend fun delete(mapping: AccountMappingEntity)

    @Query("SELECT * FROM account_mappings ORDER BY bank_name ASC")
    fun observeAll(): Flow<List<AccountMappingEntity>>

    @Query(
        "SELECT * FROM account_mappings WHERE bank_name = :bankName AND account_last4 = :accountLast4 LIMIT 1"
    )
    suspend fun findByBankAndLast4(bankName: String, accountLast4: String): AccountMappingEntity?
}
