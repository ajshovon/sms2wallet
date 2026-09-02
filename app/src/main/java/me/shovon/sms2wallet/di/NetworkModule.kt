package me.shovon.sms2wallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton
import me.shovon.sms2wallet.data.prefs.SecureTokenStore
import me.shovon.sms2wallet.data.remote.KtorWalletApiClient
import me.shovon.sms2wallet.data.remote.WalletApiClient

/**
 * Provides the networking engine and [WalletApiClient] used to talk to the Wallet REST API.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClientEngine(): HttpClientEngine = OkHttp.create()

    /**
     * The token provider reads [SecureTokenStore] lazily on every call (never once at wiring
     * time), since the user can update or clear their token from settings at any point during
     * the process lifetime. The token itself must never be logged - see [SecureTokenStore].
     */
    @Provides
    @Singleton
    fun provideWalletApiClient(
        engine: HttpClientEngine,
        secureTokenStore: SecureTokenStore,
    ): WalletApiClient = KtorWalletApiClient(
        engine = engine,
        tokenProvider = { secureTokenStore.getToken() },
    )
}
