package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A transaction extracted from an SMS by a `:bd-sms-parsers` bank parser, tracked through its
 * entire lifecycle towards being pushed to the Wallet API.
 *
 * [transactionHash] carries a unique index - this is the app's "L1" (layer 1) deduplication:
 * re-parsing the same SMS (e.g. on a rescan) produces the same hash, and the insert is silently
 * ignored (see `TransactionDao.insertIgnore`) rather than creating a second local row. Layer 2
 * dedup happens server-side once the row is queued (see `TransactionDao.findPotentialDuplicate`),
 * to catch the case where the same real-world payment is reported by more than one source (e.g.
 * a bank debit SMS and a merchant confirmation SMS for the same purchase).
 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["transaction_hash"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Stable hash of the source SMS (see `ParsedTransaction.generateTransactionId`). Unique per row. */
    @ColumnInfo(name = "transaction_hash")
    val transactionHash: String,

    @ColumnInfo(name = "bank_name")
    val bankName: String,

    /** Last 4 digits of the source account/card, when the SMS exposes them. */
    @ColumnInfo(name = "account_last4")
    val accountLast4: String?,

    /** [java.math.BigDecimal.toPlainString] - stored as text to avoid floating-point drift. */
    @ColumnInfo(name = "amount")
    val amount: String,

    /** Name of a `me.shovon.bdparser.TransactionType` enum constant. */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "merchant")
    val merchant: String?,

    @ColumnInfo(name = "reference")
    val reference: String?,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "sms_sender")
    val smsSender: String,

    @ColumnInfo(name = "sms_body")
    val smsBody: String,

    /** Epoch millis of the source SMS. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** Name of a [me.shovon.sms2wallet.domain.model.PushState] enum constant. */
    @ColumnInfo(name = "push_state")
    val pushState: String,

    /** Wallet server id of the created record, set only once [pushState] is `PUSHED`. */
    @ColumnInfo(name = "wallet_record_id")
    val walletRecordId: String?,

    @ColumnInfo(name = "wallet_account_id")
    val walletAccountId: String?,

    @ColumnInfo(name = "wallet_category_id")
    val walletCategoryId: String?,

    /** Human-readable reason for the most recent failure, if any. */
    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    /** Set by the L2 (cross-provider) duplicate check when this row looks like a repeat of another. */
    @ColumnInfo(name = "suspected_duplicate_of_id")
    val suspectedDuplicateOfId: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
