package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An SMS that no `:bd-sms-parsers` bank parser could handle, kept so the user can review it
 * (and, in future, retry parsing after a parser update).
 *
 * [smsHash] is a hash of `sender|body|timestamp`, unique-indexed so re-scanning the SMS inbox
 * does not insert duplicate rows for the same message.
 */
@Entity(
    tableName = "unmatched_sms",
    indices = [Index(value = ["sms_hash"], unique = true)]
)
data class UnmatchedSmsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Hash of sender+body+timestamp, used only for the uniqueness constraint. */
    @ColumnInfo(name = "sms_hash")
    val smsHash: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** Why no parser matched, e.g. "NO_PARSER_FOR_SENDER", "PARSE_FAILED". */
    @ColumnInfo(name = "reason")
    val reason: String
)
