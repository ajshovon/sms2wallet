package me.shovon.sms2wallet.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.prefs.AppPreferences

/**
 * Background worker that delivers the daily spending reminder without requiring
 * hazardous exact-alarm permissions.
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val transactionDao: TransactionDao,
    private val transactionNotifier: TransactionNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = appPreferences.reminderEnabled.first()
        if (!enabled) return Result.success()

        val skipCount = appPreferences.reminderSuppressThreshold.first()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val todayCount = transactionDao.observePushedCount(dayStart, dayEnd).first()

        if (skipCount > 0 && todayCount >= skipCount) {
            return Result.success()
        }

        transactionNotifier.notifyDailyReminder(todayCount)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_reminder_work"
    }
}
