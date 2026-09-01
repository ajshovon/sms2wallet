package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Latest known rate-limit usage for the configured token.
 *
 * There is no dedicated "usage stats" REST endpoint in the Wallet API; this
 * is derived purely from the `X-RateLimit-Limit` / `X-RateLimit-Remaining`
 * headers observed on the most recent response from any call this client
 * has made (see `WalletApiClient.usageStats`). Both fields are `null` until
 * at least one call has completed.
 */
@Serializable
data class UsageStatsDto(
    val limit: Int? = null,
    val remaining: Int? = null,
)
