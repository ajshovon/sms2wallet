package me.shovon.sms2wallet.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.sms.IngestSink
import me.shovon.sms2wallet.data.sms.SmsInboxReader
import me.shovon.sms2wallet.data.sms.SmsParsingService

/**
 * Drives the SMS inbox backfill: reads messages newer than the stored high-water mark, parses
 * each one, and hands the outcome to the [IngestSink].
 *
 * Live messages arrive separately through `SmsBroadcastReceiver`; this class exists for the
 * initial catch-up right after the user grants `READ_SMS`, and for any later manual rescan.
 *
 * Re-running a scan is always safe: [IngestSink] de-duplicates on `transaction_hash`, so
 * re-parsing a message that was already ingested is a no-op rather than a duplicate row.
 *
 * A [Mutex] serialises scans because the high-water mark is a read-modify-write: two concurrent
 * scans (e.g. the post-grant scan and a user-triggered rescan) could otherwise interleave and
 * write back a mark that skips messages neither of them actually processed.
 */
@Singleton
class SmsScanRepository @Inject constructor(
    private val smsInboxReader: SmsInboxReader,
    private val smsParsingService: SmsParsingService,
    private val ingestSink: IngestSink,
    private val appPreferences: AppPreferences,
) {

    private val scanMutex = Mutex()

    /**
     * Scans the inbox and returns how many messages were examined.
     *
     * @param fromScratch when true, ignores the stored high-water mark and re-reads the whole
     *   inbox (used by the manual "rescan everything" action).
     */
    suspend fun scanInbox(fromScratch: Boolean = false): Int = scanMutex.withLock {
        val since = if (fromScratch) 0L else appPreferences.lastScannedTimestamp.first()
        // Snapshot the enabled parsers once per scan rather than per message: a scan is a
        // single logical pass, and re-reading preferences for every row would turn a large
        // backfill into thousands of DataStore reads.
        val enabledParsers = appPreferences.enabledParsers()

        var examined = 0
        var newestSeen = since
        smsInboxReader.readInboxSince(since).collect { raw ->
            val result = smsParsingService.parse(enabledParsers, raw)
            ingestSink.accept(result, raw)
            examined++
            // readInboxSince emits oldest-first, but don't rely on that for correctness: only
            // ever move the mark forward, so an out-of-order emission can't rewind it and cause
            // messages to be skipped on the next scan.
            if (raw.timestamp > newestSeen) newestSeen = raw.timestamp
        }

        // Persist the mark only after the stream completes. If the scan is cancelled partway
        // (process death, user leaves), the mark stays where it was and the next scan redoes
        // the range - which dedup makes harmless - rather than silently skipping messages.
        if (newestSeen > since) appPreferences.setLastScannedTimestamp(newestSeen)
        examined
    }
}
