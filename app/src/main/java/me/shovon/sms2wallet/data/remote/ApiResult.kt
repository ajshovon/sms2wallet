package me.shovon.sms2wallet.data.remote

/**
 * Outcome of a single Wallet REST API call.
 *
 * This is deliberately not a plain `Result<T>`: several failure modes of this
 * particular API are dangerous to collapse into a generic "it failed" bucket,
 * so each gets its own branch that callers are forced to consider:
 *
 * - [SyncInProgress] (HTTP 409 `init_sync_in_progress`) is a *temporary*,
 *   expected condition right after a token's first use, not a hard error.
 * - [RateLimited] (HTTP 429) carries the server's requested backoff so
 *   callers don't have to invent their own retry cadence.
 * - [NetworkError] carries [NetworkError.ambiguous]: a timeout that happens
 *   after the request bytes were already written to the socket means the
 *   server may or may not have processed it. Callers MUST NOT blindly retry
 *   a write (e.g. [WalletApiClient.createRecords]) when `ambiguous == true`
 *   without some idempotency/reconciliation strategy, because a naive retry
 *   could create a duplicate record.
 */
sealed class ApiResult<out T> {

    /** The call completed and the (possibly nested-partial, see [WalletApiClient.createRecords]) body was parsed. */
    data class Success<T>(val data: T, val rateLimit: RateLimitInfo?) : ApiResult<T>()

    /** HTTP 401: the token is missing, malformed, expired, or revoked. */
    data object Unauthorized : ApiResult<Nothing>()

    /**
     * HTTP 409 with body `{"error":"init_sync_in_progress", ...}`.
     *
     * Returned for a token's first use until BudgetBakers finishes the
     * initial sync for that account. [retryAfterMinutes] mirrors the
     * server's `retry_after_minutes` field when present.
     */
    data class SyncInProgress(val retryAfterMinutes: Int?) : ApiResult<Nothing>()

    /** HTTP 429: per-token rate limit (300 req/hour) exceeded. [retryAfterSeconds] mirrors the `Retry-After` header. */
    data class RateLimited(val retryAfterSeconds: Int?) : ApiResult<Nothing>()

    /** Any other non-2xx HTTP response (4xx/5xx not covered by a more specific case above). */
    data class HttpError(val status: Int, val message: String?) : ApiResult<Nothing>()

    /**
     * A transport-level failure: the request never completed at the HTTP
     * layer (no status code was obtained).
     *
     * @param ambiguous `true` when the request may already have reached the
     *   server (e.g. a read/request timeout after the request was written,
     *   or a generic I/O failure mid-exchange) so a blind retry of a write
     *   risks double-processing. `false` when the failure happened before
     *   anything was sent (DNS failure, connection refused, connect-phase
     *   timeout) and a retry is safe.
     */
    data class NetworkError(val message: String?, val ambiguous: Boolean) : ApiResult<Nothing>()

    /**
     * A request that was rejected by this client *before* any network call
     * was made (e.g. [WalletApiClient.createRecords] with an empty or
     * oversized batch). Distinguished from [HttpError] because no request
     * ever left the device.
     */
    data class InvalidRequest(val message: String) : ApiResult<Nothing>()
}
