package me.shovon.sms2wallet.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.MainActivity
import me.shovon.sms2wallet.R
import me.shovon.sms2wallet.presentation.util.MoneyFormatter

/**
 * Tells the user when a transaction has been picked up from an SMS.
 *
 * Two channels rather than one, because the two outcomes deserve different treatment: an
 * auto-pushed transaction is a receipt the user may never need to act on, while one waiting for
 * review is a task. Separate channels let the user silence the first and keep the second - which
 * a single channel would force them to choose between all-or-nothing.
 */
@Singleton
class TransactionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Posts a notification for a freshly ingested transaction.
     *
     * @param needsReview true when the transaction is waiting in the review queue; false when it
     *   was auto-pushed.
     */
    fun notifyIngested(
        transactionId: Long,
        merchant: String?,
        amount: BigDecimal,
        type: String,
        needsReview: Boolean,
    ) {
        if (!canPostNotifications()) return
        ensureChannels()

        val isIncome = type == TransactionType.INCOME.name
        val signed = if (isIncome) amount.abs() else amount.abs().negate()
        val money = MoneyFormatter.formatBdt(signed)
        val who = merchant?.takeIf { it.isNotBlank() } ?: "New transaction"

        val builder = NotificationCompat.Builder(
            context,
            if (needsReview) CHANNEL_REVIEW else CHANNEL_PUSHED,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (needsReview) "Review: $who" else "Pushed: $who")
            .setContentText(
                if (needsReview) {
                    "$money · tap to check it before it goes to Wallet"
                } else {
                    "$money · sent to Wallet automatically"
                }
            )
            .setPriority(if (needsReview) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(transactionId, needsReview))

        // The transaction's own id is the notification id, so a rescan that re-notifies the same
        // transaction replaces its notification instead of stacking duplicates.
        NotificationManagerCompat.from(context)
            .notify(transactionId.toInt(), builder.build())
    }

    /**
     * Where tapping the notification goes.
     *
     * A review notification opens that transaction's edit screen directly - the whole point is
     * that the user wanted to check or adjust it. An auto-pushed one has nothing to act on, so it
     * just opens the app.
     */
    private fun contentIntent(transactionId: Long, needsReview: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (needsReview) putExtra(EXTRA_REVIEW_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REVIEW,
                "Transactions to review",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A transaction was read from an SMS and is waiting for you to check it." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PUSHED,
                "Auto-pushed transactions",
                // Low: this is a receipt, not a task. It belongs in the shade without a sound.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "A transaction was sent to Wallet automatically." }
        )
    }

    /** POST_NOTIFICATIONS is a runtime permission from API 33; posting without it is a no-op. */
    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        /** Intent extra carrying the transaction a review notification should open. */
        const val EXTRA_REVIEW_TRANSACTION_ID = "review_transaction_id"

        private const val CHANNEL_REVIEW = "transactions_review"
        private const val CHANNEL_PUSHED = "transactions_pushed"
    }
}
