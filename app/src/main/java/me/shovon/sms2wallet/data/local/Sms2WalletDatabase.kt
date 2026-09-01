package me.shovon.sms2wallet.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.shovon.sms2wallet.data.local.dao.AccountMappingDao
import me.shovon.sms2wallet.data.local.dao.CategoryRuleDao
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.dao.UnmatchedSmsDao
import me.shovon.sms2wallet.data.local.dao.WalletAccountDao
import me.shovon.sms2wallet.data.local.dao.WalletCategoryDao
import me.shovon.sms2wallet.data.local.entity.AccountMappingEntity
import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity
import me.shovon.sms2wallet.data.local.entity.PushLogEntity
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.data.local.entity.UnmatchedSmsEntity
import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity

/**
 * No [androidx.room.RoomDatabase.Callback]-based `fallbackToDestructiveMigration` is configured
 * on purpose: this database holds the only local record of what has (or has not) been pushed to
 * the Wallet API, and destructively wiping it on a schema mismatch would blow away that
 * bookkeeping and risk resending already-pushed transactions. Every future schema change must
 * ship an explicit `Migration`.
 */
@Database(
    entities = [
        TransactionEntity::class,
        AccountMappingEntity::class,
        CategoryRuleEntity::class,
        WalletAccountEntity::class,
        WalletCategoryEntity::class,
        PushLogEntity::class,
        UnmatchedSmsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class Sms2WalletDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountMappingDao(): AccountMappingDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun walletAccountDao(): WalletAccountDao
    abstract fun walletCategoryDao(): WalletCategoryDao
    abstract fun pushLogDao(): PushLogDao
    abstract fun unmatchedSmsDao(): UnmatchedSmsDao

    companion object {
        const val DATABASE_NAME: String = "sms2wallet.db"
    }
}
