package me.shovon.sms2wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity

@Dao
interface CategoryRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity): Long

    @Update
    suspend fun update(rule: CategoryRuleEntity)

    @Delete
    suspend fun delete(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules ORDER BY priority ASC")
    fun observeAllOrdered(): Flow<List<CategoryRuleEntity>>

    /** Rules applicable to [bankName]: bank-specific rules plus the bank-agnostic (`bank_name IS NULL`) ones, priority order. */
    @Query(
        "SELECT * FROM category_rules WHERE bank_name IS NULL OR bank_name = :bankName ORDER BY priority ASC"
    )
    suspend fun findApplicableRules(bankName: String): List<CategoryRuleEntity>
}
