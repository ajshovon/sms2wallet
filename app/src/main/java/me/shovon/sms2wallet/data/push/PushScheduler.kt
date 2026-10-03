package me.shovon.sms2wallet.data.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [PushWorker].
 *
 * Every path that can put a transaction into `QUEUED` calls [schedule]: the ingest sink when a
 * mapped source auto-pushes, and the UI when the user approves one. Without this the queue only
 * ever grew.
 */
@Singleton
class PushScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Enqueues a push pass.
     *
     * Uses [ExistingWorkPolicy.APPEND_OR_REPLACE] under a single unique name so approving or
     * ingesting transactions while a drain is completing ensures a subsequent drain pass runs
     * rather than being silently dropped by KEEP.
     */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<PushWorker>()
            .setConstraints(
                Constraints.Builder()
                    // No point waking up to POST with no connection; the send would only be
                    // requeued anyway.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(PushWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        /** Starting point for exponential backoff on rate limits, 5xx, and offline retries. */
        const val BACKOFF_SECONDS = 30L
    }
}
