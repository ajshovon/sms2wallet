package me.shovon.sms2wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Supplies WorkManager's [Configuration] so it can build `@HiltWorker` workers.
 *
 * `PushWorker` has constructor dependencies, which WorkManager's default factory cannot
 * provide - without this it fails to instantiate the worker and the send queue silently never
 * drains. The manifest correspondingly removes `androidx.startup`'s default WorkManager
 * initializer so this on-demand configuration is the one that takes effect.
 */
@HiltAndroidApp
class Sms2WalletApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
