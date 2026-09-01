package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity

@Dao
interface WalletCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<WalletCategoryEntity>)

    @Query("SELECT * FROM wallet_categories ORDER BY name ASC")
    fun observeAll(): Flow<List<WalletCategoryEntity>>

    @Query("SELECT * FROM wallet_categories WHERE id = :id")
    suspend fun findById(id: String): WalletCategoryEntity?

    /** Wipes the cache so a fresh `GET /categories` response can fully replace it (drops any server-side deletions/renames). */
    @Query("DELETE FROM wallet_categories")
    suspend fun clearAll()
}
