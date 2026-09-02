package me.shovon.sms2wallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.prefs.SecureTokenStore
import me.shovon.sms2wallet.data.push.TransactionReconciler
import me.shovon.sms2wallet.data.push.TransactionSender
import me.shovon.sms2wallet.data.remote.WalletApiClient

/** Wires the send pipeline that pushes approved transactions to the Wallet API. */
@Module
@InstallIn(SingletonComponent::class)
object PushModule {

    /**
     * [TransactionSender.hasToken] is read fresh on every send pass rather than snapshotted,
     * so adding a token in Settings makes the already-queued backlog sendable without a restart.
     */
    @Provides
    @Singleton
    fun provideTransactionReconciler(
        transactionDao: TransactionDao,
        pushLogDao: PushLogDao,
        walletApiClient: WalletApiClient,
    ): TransactionReconciler = TransactionReconciler(
        transactionDao = transactionDao,
        pushLogDao = pushLogDao,
        walletApiClient = walletApiClient,
    )

    @Provides
    @Singleton
    fun provideTransactionSender(
        transactionDao: TransactionDao,
        pushLogDao: PushLogDao,
        walletApiClient: WalletApiClient,
        secureTokenStore: SecureTokenStore,
    ): TransactionSender = TransactionSender(
        transactionDao = transactionDao,
        pushLogDao = pushLogDao,
        walletApiClient = walletApiClient,
        hasToken = { secureTokenStore.hasToken.first() },
    )
}
