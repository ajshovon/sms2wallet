package me.shovon.sms2wallet.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives `android.provider.Telephony.SMS_RECEIVED` and hands each message to
 * [SmsParsingService], then [IngestSink]. Deliberately plain (not `@AndroidEntryPoint`): the
 * OS constructs broadcast receivers with a no-arg constructor, so dependencies are pulled from
 * the Hilt graph on demand via [SmsIngestEntryPoint] instead of being injected.
 *
 * A single system SMS can arrive split across multiple PDUs (e.g. long messages, or ones with
 * embedded Unicode); [Telephony.Sms.Intents.getMessagesFromIntent] returns one `SmsMessage` per
 * part, which are reassembled here into a single [RawSms] before parsing.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val first = parts.first()
        val sender = first.originatingAddress ?: return
        val timestamp = first.timestampMillis
        val body = parts.joinToString(separator = "") { it.messageBody ?: "" }
        if (body.isEmpty()) return

        val raw = RawSms(id = NOT_PERSISTED_ID, sender = sender, body = body, timestamp = timestamp)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsIngestEntryPoint::class.java
        )

        // Broadcast receivers must finish onReceive() quickly; goAsync() extends the receiver's
        // lifetime just long enough to run the (suspend) parsing + sink hand-off on a background
        // dispatcher, and pendingResult.finish() releases it so the OS can recycle the receiver.
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val enabledParsers = entryPoint.appPreferences().enabledParsers()
                val result = entryPoint.smsParsingService().parse(enabledParsers, raw)
                entryPoint.ingestSink().accept(result, raw)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        /** [RawSms.id] placeholder: broadcast-delivered SMS aren't yet rows in the SMS provider. */
        const val NOT_PERSISTED_ID = -1L
    }
}
