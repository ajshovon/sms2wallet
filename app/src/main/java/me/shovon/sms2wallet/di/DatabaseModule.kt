package me.shovon.sms2wallet.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.shovon.sms2wallet.data.local.Sms2WalletDatabase
import me.shovon.sms2wallet.data.local.dao.AccountMappingDao
import me.shovon.sms2wallet.data.local.dao.CategoryRuleDao
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.dao.UnmatchedSmsDao
import me.shovon.sms2wallet.data.local.dao.WalletAccountDao
import me.shovon.sms2wallet.data.local.dao.WalletCategoryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Sms2WalletDatabase =
        Room.databaseBuilder(
            context,
            Sms2WalletDatabase::class.java,
            Sms2WalletDatabase.DATABASE_NAME
        ).build()

    @Provides
    fun provideTransactionDao(database: Sms2WalletDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideAccountMappingDao(database: Sms2WalletDatabase): AccountMappingDao = database.accountMappingDao()

    @Provides
    fun provideCategoryRuleDao(database: Sms2WalletDatabase): CategoryRuleDao = database.categoryRuleDao()

    @Provides
    fun provideWalletAccountDao(database: Sms2WalletDatabase): WalletAccountDao = database.walletAccountDao()

    @Provides
    fun provideWalletCategoryDao(database: Sms2WalletDatabase): WalletCategoryDao = database.walletCategoryDao()

    @Provides
    fun providePushLogDao(database: Sms2WalletDatabase): PushLogDao = database.pushLogDao()

    @Provides
    fun provideUnmatchedSmsDao(database: Sms2WalletDatabase): UnmatchedSmsDao = database.unmatchedSmsDao()
}
