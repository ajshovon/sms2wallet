package me.shovon.sms2wallet.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.dao.WalletAccountDao
import me.shovon.sms2wallet.data.local.dao.WalletCategoryDao
import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.WalletApiClient
import me.shovon.sms2wallet.data.remote.dto.WalletAccountDto
import me.shovon.sms2wallet.data.remote.dto.WalletCategoryDto

/**
 * Refreshes the local [WalletAccountEntity]/[WalletCategoryEntity] caches from
 * [WalletApiClient] and exposes them as [Flow]s for a (future) account/category picker UI.
 *
 * [ApiResult] failures are returned to the caller as-is rather than collapsed into a boolean
 * or swallowed. In particular [ApiResult.SyncInProgress] must stay distinguishable from every
 * other failure branch, since the UI has to tell the user to wait `retryAfterMinutes` rather
 * than showing a generic "failed" message - see [ApiResult]'s own kdoc for why each branch
 * matters.
 */
class WalletSyncRepository @Inject constructor(
    private val walletApiClient: WalletApiClient,
    private val walletAccountDao: WalletAccountDao,
    private val walletCategoryDao: WalletCategoryDao,
) {

    val accounts: Flow<List<WalletAccountEntity>> = walletAccountDao.observeAll()
    val categories: Flow<List<WalletCategoryEntity>> = walletCategoryDao.observeAll()

    /**
     * Fetches every Wallet account and fully replaces the local cache (see
     * [WalletAccountDao.clearAll]) so server-side deletions/renames are reflected. The cache is
     * only touched on [ApiResult.Success]; any other outcome is returned to the caller
     * untouched and the existing cache is left as-is.
     */
    suspend fun refreshAccounts(): ApiResult<Unit> =
        when (val result = walletApiClient.listAccounts()) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                walletAccountDao.clearAll()
                walletAccountDao.upsertAll(result.data.map { it.toEntity(now) })
                ApiResult.Success(Unit, result.rateLimit)
            }
            is ApiResult.Unauthorized -> result
            is ApiResult.SyncInProgress -> result
            is ApiResult.RateLimited -> result
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidRequest -> result
        }

    /** Fetches every Wallet category and fully replaces the local cache; see [refreshAccounts]. */
    suspend fun refreshCategories(): ApiResult<Unit> =
        when (val result = walletApiClient.listCategories()) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                walletCategoryDao.clearAll()
                walletCategoryDao.upsertAll(result.data.map { it.toEntity(now) })
                ApiResult.Success(Unit, result.rateLimit)
            }
            is ApiResult.Unauthorized -> result
            is ApiResult.SyncInProgress -> result
            is ApiResult.RateLimited -> result
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidRequest -> result
        }

    /**
     * [WalletAccountDto] does not (yet) expose an account-type field from `GET /accounts`, so
     * [WalletAccountEntity.accountType] is cached as an empty string until the DTO/API surface
     * grows one. Every other field maps 1:1.
     */
    private fun WalletAccountDto.toEntity(cachedAt: Long): WalletAccountEntity = WalletAccountEntity(
        id = id,
        name = name.orEmpty(),
        currencyCode = currencyCode.orEmpty(),
        accountType = "",
        cachedAt = cachedAt,
    )

    private fun WalletCategoryDto.toEntity(cachedAt: Long): WalletCategoryEntity = WalletCategoryEntity(
        id = id,
        name = name.orEmpty(),
        systemId = systemId,
        parentId = parentId,
        color = color,
        cachedAt = cachedAt,
    )
}
