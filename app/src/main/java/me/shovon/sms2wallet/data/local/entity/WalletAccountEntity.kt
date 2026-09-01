package me.shovon.sms2wallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of a single Wallet account, refreshed from `GET /accounts`. */
@Entity(tableName = "wallet_accounts")
data class WalletAccountEntity(
    /** The Wallet server's own UUID for this account. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "account_type")
    val accountType: String,

    /** Epoch millis when this row was last refreshed from the API. */
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long
)
