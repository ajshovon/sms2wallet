package me.shovon.sms2wallet.data.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily spending reminder via WorkManager.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var targetTime = now.with(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))).withSecond(0).withNano(0)
        if (targetTime.isBefore(now)) {
            targetTime = targetTime.plusDays(1)
        }
        val initialDelay = Duration.between(now, targetTime).toMillis()

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                DailyReminderWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(DailyReminderWorker.WORK_NAME)
    }
}
