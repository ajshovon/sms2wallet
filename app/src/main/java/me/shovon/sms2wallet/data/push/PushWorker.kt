package me.shovon.sms2wallet.data.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background job that drains the send queue.
 *
 * Kept as a thin shell around [TransactionSender]: the worker owns only the retry decision, so
 * all the state-machine logic stays testable without WorkManager.
 *
 * Runs the sender in a loop so a backlog larger than one batch clears in a single execution
 * instead of needing one scheduled run per 10 transactions.
 */
@HiltWorker
class PushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sender: TransactionSender,
    private val reconciler: TransactionReconciler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Resolve anything left in an unknown state first, so a row stranded by an earlier
        // crash or timeout is settled (or safely re-queued) before this pass sends anything.
        runCatching { reconciler.reconcile() }

        repeat(MAX_BATCHES_PER_RUN) {
            when (val outcome = sender.sendQueued()) {
                is TransactionSender.Outcome.Done -> Unit
                // Nothing to push without credentials. Not a failure: the rows stay queued and
                // the next run (after the user adds a token) picks them up.
                is TransactionSender.Outcome.NoToken -> return Result.success()
                // WorkManager applies the backoff policy set in PushScheduler.
                is TransactionSender.Outcome.Retry -> return Result.retry()
            }
            // Done with nothing left to claim - stop early rather than spinning the loop.
            if (!sender.hasQueuedWork()) return Result.success()
        }
        // Still more queued than one run should push in a single wake-up; come back for the rest.
        return Result.retry()
    }

    companion object {
        /** Bounds one execution so a huge backlog cannot hold a wakelock indefinitely. */
        private const val MAX_BATCHES_PER_RUN = 5

        /** Unique work name, so queueing the same job repeatedly does not stack duplicate runs. */
        const val WORK_NAME = "sms2wallet-push"
    }
}
