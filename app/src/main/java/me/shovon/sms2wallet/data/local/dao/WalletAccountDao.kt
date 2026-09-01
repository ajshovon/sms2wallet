package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity

@Dao
interface WalletAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(accounts: List<WalletAccountEntity>)

    @Query("SELECT * FROM wallet_accounts ORDER BY name ASC")
    fun observeAll(): Flow<List<WalletAccountEntity>>

    @Query("SELECT * FROM wallet_accounts WHERE id = :id")
    suspend fun findById(id: String): WalletAccountEntity?

    /** Wipes the cache so a fresh `GET /accounts` response can fully replace it (drops any server-side deletions/renames). */
    @Query("DELETE FROM wallet_accounts")
    suspend fun clearAll()
}
