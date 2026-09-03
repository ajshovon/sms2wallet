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

    /**
     * Any mapping for [bankName], regardless of account last-4.
     *
     * The exact (bank, last-4) lookup is too strict on its own: the same provider does not put
     * the account digits in every message, so a user who mapped "bKash" from a message that
     * exposed no digits would get no match on the next message that did - and the transaction
     * would arrive with no account, unroutable and unpushable, despite a mapping existing.
     *
     * Mappings with no last-4 sort first because they are the deliberate "this whole provider
     * goes here" case.
     */
    @Query(
        """
        SELECT * FROM account_mappings
        WHERE bank_name = :bankName
        ORDER BY CASE WHEN account_last4 = '' THEN 0 ELSE 1 END, id ASC
        LIMIT 1
        """
    )
    suspend fun findAnyByBank(bankName: String): AccountMappingEntity?
}
