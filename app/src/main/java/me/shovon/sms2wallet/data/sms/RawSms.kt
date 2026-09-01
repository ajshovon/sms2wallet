package me.shovon.sms2wallet.data.sms

/**
 * A single inbound SMS, read either from [SmsInboxReader] (backfill) or reassembled by
 * [SmsBroadcastReceiver] (live delivery), before any bank-parser has looked at it.
 *
 * @param id Content-provider row id for inbox reads; `-1` for broadcast-received SMS, which
 * are not yet persisted to the system SMS provider at delivery time.
 * @param sender The raw SMS sender/address, e.g. a bank shortcode or an alphanumeric sender id.
 * @param body The full (already reassembled, for multipart messages) SMS text.
 * @param timestamp Epoch milliseconds the message was received.
 */
data class RawSms(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Long
)
