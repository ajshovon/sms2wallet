package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of a single Wallet category, refreshed from `GET /categories`. */
@Entity(tableName = "wallet_categories")
data class WalletCategoryEntity(
    /** The Wallet server's own UUID for this category. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** Id of a built-in Wallet system category this maps to, if any. */
    @ColumnInfo(name = "system_id")
    val systemId: String?,

    /** Id of the parent category, for sub-categories. */
    @ColumnInfo(name = "parent_id")
    val parentId: String?,

    @ColumnInfo(name = "color")
    val color: String?,

    /** Epoch millis when this row was last refreshed from the API. */
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long
)
