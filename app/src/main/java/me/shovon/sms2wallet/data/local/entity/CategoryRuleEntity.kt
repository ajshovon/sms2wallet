package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An ordered merchant-keyword rule used to auto-assign a Wallet category to a parsed
 * transaction. Rules are evaluated in ascending [priority] order; the first keyword match wins.
 */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Case-insensitive substring matched against the transaction's merchant/reference text. */
    @ColumnInfo(name = "keyword")
    val keyword: String,

    @ColumnInfo(name = "wallet_category_id")
    val walletCategoryId: String,

    /** Lower value = evaluated first. */
    @ColumnInfo(name = "priority")
    val priority: Int,

    /** Restricts this rule to a single bank's transactions; `null` applies to all banks. */
    @ColumnInfo(name = "bank_name")
    val bankName: String?
)
