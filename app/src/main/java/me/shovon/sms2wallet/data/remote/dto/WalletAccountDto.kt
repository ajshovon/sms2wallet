package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A Wallet account as returned by `GET /accounts`.
 *
 * Only the fields this client currently needs are modeled; unknown fields
 * are ignored (see the `Json { ignoreUnknownKeys = true }` configuration in
 * `KtorWalletApiClient`), so this can be extended without breaking parsing.
 */
@Serializable
data class WalletAccountDto(
    val id: String,
    val name: String? = null,
    val currencyCode: String? = null,
)
