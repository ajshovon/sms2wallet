package me.shovon.sms2wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.notification.ReminderScheduler
import me.shovon.sms2wallet.data.prefs.AppPreferences

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

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleReminderIfEnabled()
    }

    /**
     * The reminder preference defaults to on, and nothing else enqueues the worker: without this
     * the reminder only ever runs for a user who happened to toggle the setting. KEEP so an
     * already-scheduled reminder is never pushed forward by opening the app.
     */
    private fun scheduleReminderIfEnabled() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (!appPreferences.reminderEnabled.first()) return@launch
            val timeMinutes = appPreferences.reminderTimeMinutes.first()
            reminderScheduler.schedule(
                hour = timeMinutes / MINUTES_PER_HOUR,
                minute = timeMinutes % MINUTES_PER_HOUR,
                policy = ExistingPeriodicWorkPolicy.KEEP,
            )
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
