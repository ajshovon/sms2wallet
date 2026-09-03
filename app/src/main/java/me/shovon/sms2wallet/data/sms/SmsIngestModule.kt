package me.shovon.sms2wallet.data.sms

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import me.shovon.sms2wallet.data.local.dao.AccountMappingDao
import me.shovon.sms2wallet.data.local.dao.CategoryRuleDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.dao.UnmatchedSmsDao
import me.shovon.sms2wallet.data.local.dao.WalletCategoryDao
import me.shovon.sms2wallet.data.notification.TransactionNotifier
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.push.PushScheduler

/**
 * Binds [IngestSink] to the Room-backed [RoomIngestSink]. [NoOpIngestSink] is kept in the tree
 * (e.g. for tests/tooling that want a stub sink) but is no longer bound here.
 */
@Module
@InstallIn(SingletonComponent::class)
object SmsIngestModule {

    @Provides
    @Singleton
    fun provideIngestSink(
        transactionDao: TransactionDao,
        accountMappingDao: AccountMappingDao,
        categoryRuleDao: CategoryRuleDao,
        unmatchedSmsDao: UnmatchedSmsDao,
        appPreferences: AppPreferences,
        pushScheduler: PushScheduler,
        walletCategoryDao: WalletCategoryDao,
        notifier: TransactionNotifier,
    ): IngestSink = RoomIngestSink(
        transactionDao = transactionDao,
        accountMappingDao = accountMappingDao,
        categoryRuleDao = categoryRuleDao,
        unmatchedSmsDao = unmatchedSmsDao,
        // Read lazily on every call (mirrors KtorWalletApiClient's tokenProvider) since the user
        // can change which banks auto-push at any time from settings.
        autoPushBankNames = { appPreferences.autoPushParserNames.first() },
        walletCategories = { walletCategoryDao.observeAll().first() },
        onQueued = { pushScheduler.schedule() },
        onIngested = { id, merchant, amount, type, needsReview ->
            notifier.notifyIngested(id, merchant, amount, type, needsReview)
        },
    )
}
