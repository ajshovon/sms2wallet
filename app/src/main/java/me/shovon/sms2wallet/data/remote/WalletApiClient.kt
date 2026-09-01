package me.shovon.sms2wallet.data.remote

import me.shovon.sms2wallet.data.remote.dto.CreateRecordRequest
import me.shovon.sms2wallet.data.remote.dto.CreateRecordsResponse
import me.shovon.sms2wallet.data.remote.dto.RecordDto
import me.shovon.sms2wallet.data.remote.dto.UsageStatsDto
import me.shovon.sms2wallet.data.remote.dto.WalletAccountDto
import me.shovon.sms2wallet.data.remote.dto.WalletCategoryDto

/**
 * Client for the BudgetBakers Wallet REST API
 * (`https://rest.budgetbakers.com/wallet/v1/api`).
 *
 * Self-contained: every method takes primitives or this package's own DTOs
 * and returns [ApiResult]. Callers own translating results to/from local
 * storage — this client has no knowledge of Room, DataStore, or any other
 * persistence layer.
 */
interface WalletApiClient {

    /** All accounts for the configured token, auto-paginated to completion. */
    suspend fun listAccounts(): ApiResult<List<WalletAccountDto>>

    /** All categories for the configured token, auto-paginated to completion. */
    suspend fun listCategories(): ApiResult<List<WalletCategoryDto>>

    /**
     * Creates 1..50 records in a single batch request.
     *
     * The batch is NOT atomic — see [CreateRecordsResponse] for why the
     * result must always be inspected per-item, never as a single
     * success/failure boolean. A [requests] list outside `1..50` is
     * rejected as [ApiResult.InvalidRequest] before any network call.
     */
    suspend fun createRecords(requests: List<CreateRecordRequest>): ApiResult<CreateRecordsResponse>

    /**
     * Looks up records matching an exact account/day/amount/source, for
     * reconciliation (e.g. "did we already push this SMS-derived
     * transaction?").
     *
     * @param dayIso an `eq.`-filterable date, e.g. `2026-01-15`
     * @param amount an `eq.`-filterable amount, e.g. `-123.45`
     */
    suspend fun findRecords(
        accountId: String,
        dayIso: String,
        amount: String,
        source: String = "rest",
    ): ApiResult<List<RecordDto>>

    /** Cheap call (`GET /accounts?limit=1`) to check whether the configured token is valid. */
    suspend fun validateToken(): ApiResult<Unit>

    /** Latest known rate-limit usage for the configured token. See [UsageStatsDto]. */
    suspend fun usageStats(): ApiResult<UsageStatsDto>
}
