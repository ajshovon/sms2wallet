package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Maps a parsed SMS source (bank + last 4 digits) to a Wallet account, so incoming
 * transactions can be routed and, optionally, auto-pushed without manual review.
 *
 * Room's unique index treats every `NULL` as distinct from every other `NULL`, so two mappings
 * for the same bank with a `null` [accountLast4] would NOT collide even though they should be
 * the same logical mapping. To keep the (bank_name, account_last4) unique index meaningful, a
 * `null`/unknown last-4 is stored as [UNKNOWN_LAST4] (an empty string) rather than `null`.
 */
@Entity(
    tableName = "account_mappings",
    indices = [Index(value = ["bank_name", "account_last4"], unique = true)]
)
data class AccountMappingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "bank_name")
    val bankName: String,

    /** Empty string ([UNKNOWN_LAST4]) sentinel when the SMS source has no distinguishable last 4 digits. */
    @ColumnInfo(name = "account_last4")
    val accountLast4: String,

    @ColumnInfo(name = "wallet_account_id")
    val walletAccountId: String,

    @ColumnInfo(name = "wallet_account_name")
    val walletAccountName: String,

    /** When true, transactions matching this mapping may be queued for send without manual review. */
    @ColumnInfo(name = "auto_push")
    val autoPush: Boolean,

    @ColumnInfo(name = "default_category_id")
    val defaultCategoryId: String?
) {
    companion object {
        /** Sentinel used for [accountLast4] when the source has no distinguishable last 4 digits. */
        const val UNKNOWN_LAST4: String = ""
    }
}
