package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Money amount for a record.
 *
 * The Wallet API has no separate income/expense (transaction type) field:
 * the sign of [value] alone determines it — negative is an expense,
 * positive is income. [currencyCode], when supplied, must equal the
 * owning account's currency; when omitted the server defaults to it.
 */
@Serializable
data class RecordAmount(
    val value: Double,
    val currencyCode: String? = null,
)
