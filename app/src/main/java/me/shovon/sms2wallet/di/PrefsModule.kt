package me.shovon.sms2wallet.di

import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.prefs.SecureTokenStore
import javax.inject.Singleton

/**
 * Provides the preferences/secure-storage layer. [AppPreferences] and [SecureTokenStore] are
 * plain `@Inject constructor` singletons, so this module only needs to exist to satisfy Hilt's
 * component graph in case either class is referenced from a Java/interface boundary later; the
 * explicit `@Provides` below are kept as thin pass-throughs for discoverability.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrefsModule {

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)

    @Provides
    @Singleton
    fun provideSecureTokenStore(@ApplicationContext context: Context): SecureTokenStore =
        SecureTokenStore(context)
}
