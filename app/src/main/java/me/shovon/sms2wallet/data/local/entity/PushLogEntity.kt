package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit trail row for a single call made against the Wallet API on behalf of a transaction.
 * Kept even for transactions that are later deleted (no foreign key), so the audit history
 * always survives.
 */
@Entity(tableName = "push_log")
data class PushLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Id of the related `transactions` row, when applicable. */
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long?,

    /** e.g. "CREATE_RECORD", "VERIFY_RECORD". */
    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "http_status")
    val httpStatus: Int?,

    @ColumnInfo(name = "success")
    val success: Boolean,

    @ColumnInfo(name = "message")
    val message: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
