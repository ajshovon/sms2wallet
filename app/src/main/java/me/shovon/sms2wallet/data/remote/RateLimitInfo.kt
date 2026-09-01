package me.shovon.sms2wallet.data.remote

/**
 * Snapshot of the `X-RateLimit-Limit` / `X-RateLimit-Remaining` response
 * headers from the most recent Wallet API call. The token bucket is
 * per-token on the server, so this is only meaningful for the token
 * currently configured on this client.
 */
data class RateLimitInfo(
    val limit: Int?,
    val remaining: Int?,
)
