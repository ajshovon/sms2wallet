package me.shovon.sms2wallet.data.sms

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Streams messages out of the system SMS inbox (`Telephony.Sms.CONTENT_URI`) for the
 * one-off/periodic backfill scan. Live SMS are instead handled by [SmsBroadcastReceiver] and
 * never touch this class.
 *
 * Results are emitted one row at a time via a cold [Flow] backed by a single forward-only
 * `Cursor` pass, so a caller processing a large inbox never has to hold every row in memory at
 * once, and the cursor is guaranteed to be closed ([android.database.Cursor.use]) even if the
 * collector cancels partway through.
 */
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Emits every inbox SMS with `DATE >= sinceTimestamp`, oldest first, so a caller can
     * advance its own high-water mark ([me.shovon.sms2wallet.data.prefs.AppPreferences.lastScannedTimestamp])
     * as it consumes the stream and safely resume from the last-seen message after a crash.
     *
     * @param sinceTimestamp Epoch millis; pass `0` to read the entire inbox.
     */
    fun readInboxSince(sinceTimestamp: Long): Flow<RawSms> = flow {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY
        )
        val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            sinceTimestamp.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)

            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressColumn) ?: continue
                val body = cursor.getString(bodyColumn) ?: continue
                emit(
                    RawSms(
                        id = cursor.getLong(idColumn),
                        sender = sender,
                        body = body,
                        timestamp = cursor.getLong(dateColumn)
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)
}
