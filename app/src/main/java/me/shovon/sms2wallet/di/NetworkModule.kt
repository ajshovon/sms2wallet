package me.shovon.sms2wallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * Provides the real networking engine used by [me.shovon.sms2wallet.data.remote.KtorWalletApiClient].
 *
 * This module deliberately does NOT bind [me.shovon.sms2wallet.data.remote.WalletApiClient]
 * itself: constructing it also requires a `suspend () -> String?` token
 * provider backed by wherever the bearer token ends up persisted, which is
 * owned by a different part of the app. Once that storage lands, add a
 * `@Provides fun provideWalletApiClient(engine: HttpClientEngine, ...): WalletApiClient`
 * here (or in a follow-up module) that wires the two together.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClientEngine(): HttpClientEngine = OkHttp.create()
}
