package me.shovon.sms2wallet.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.WalletApiClient
import me.shovon.sms2wallet.data.repository.IntelligenceRepository
import me.shovon.sms2wallet.data.repository.IntelligenceResult
import me.shovon.sms2wallet.data.repository.SettingsRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.domain.nlp.NlPrefill
import me.shovon.sms2wallet.presentation.model.DashboardUiState
import me.shovon.sms2wallet.presentation.model.QuickAddUiState
import me.shovon.sms2wallet.presentation.model.RateLimitUiState
import me.shovon.sms2wallet.presentation.model.TokenHealth
import me.shovon.sms2wallet.presentation.util.TimeFormatter

/**
 * Dashboard state: local counters straight from Room, plus token health and API budget, which
 * can only come from the network.
 *
 * The remote half is fetched once per ViewModel (and on explicit [refresh]) rather than being
 * observed, because every check spends from the same 300-requests/hour budget the card is
 * reporting - polling it would be self-defeating.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val walletApiClient: WalletApiClient,
    private val intelligenceRepository: IntelligenceRepository,
) : ViewModel() {

    private val remoteState = MutableStateFlow(RemoteState())

    private val quickAddState = MutableStateFlow(QuickAddUiState())

    private val baseState: Flow<DashboardUiState> = combine(
        transactionRepository.observePushedTodayCount(),
        transactionRepository.observePushedThisWeekCount(),
        transactionRepository.observeReviewQueue().map { it.size },
        settingsRepository.lastScannedTimestamp,
        remoteState,
    ) { pushedToday, pushedThisWeek, pendingReview, lastScanned, remote ->
        DashboardUiState(
            pushedToday = pushedToday,
            pushedThisWeek = pushedThisWeek,
            // Deliberately the review-queue size, not observePendingCount(): this card navigates
            // to the review queue, so showing a different number than that screen lists would be
            // a bug the user cannot explain.
            pendingReviewCount = pendingReview,
            lastSyncLabel = TimeFormatter.relativeLabel(lastScanned),
            tokenHealth = remote.tokenHealth,
            rateLimit = remote.rateLimit,
            isLoading = remote.isLoading,
        )
    }

    // Folded in separately rather than as a sixth source: `combine` tops out at five typed
    // flows, and the quick-add box changes on every keystroke while the rest of the dashboard
    // does not - keeping them apart stops typing from recomputing the whole card set.
    val uiState: StateFlow<DashboardUiState> = combine(
        baseState,
        quickAddState,
        intelligenceRepository.isConfigured,
    ) { base, quickAdd, isConfigured ->
        base.copy(quickAdd = quickAdd.copy(isAvailable = isConfigured))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardUiState(isLoading = true),
    )

    fun onQuickAddInputChange(value: String) {
        quickAddState.value = quickAddState.value.copy(input = value, errorMessage = null)
    }

    /**
     * Sends the typed phrase to the model and hands the result to [onParsed] for navigation.
     *
     * The input is cleared only on success. A failed parse leaves the phrase in the box so the
     * user can edit it instead of retyping it - which also matters because a retry costs another
     * API call against their own quota.
     */
    fun submitQuickAdd(onParsed: (NlPrefill) -> Unit) {
        val input = quickAddState.value.input.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            quickAddState.value = quickAddState.value.copy(isParsing = true, errorMessage = null)

            when (val result = intelligenceRepository.parse(input)) {
                is IntelligenceResult.Success -> {
                    quickAddState.value = QuickAddUiState(isAvailable = true)
                    onParsed(result.prefill)
                }

                IntelligenceResult.NotConfigured -> {
                    quickAddState.value = quickAddState.value.copy(
                        isParsing = false,
                        errorMessage = "Add a Gemini API key in Settings to use this.",
                    )
                }

                is IntelligenceResult.Failure -> {
                    quickAddState.value = quickAddState.value.copy(
                        isParsing = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    init {
        // Re-check whenever the stored token changes rather than only once at construction:
        // connecting in Settings previously left this screen showing "Token status unknown"
        // until the ViewModel happened to be recreated.
        viewModelScope.launch {
            settingsRepository.hasToken.distinctUntilChanged().collect { refresh() }
        }
    }

    /** Re-checks token validity and the rate-limit budget. Safe to call repeatedly; costs 1-2 API calls. */
    fun refresh() {
        viewModelScope.launch {
            if (!settingsRepository.hasToken.first()) {
                remoteState.value = RemoteState(tokenHealth = TokenHealth.UNKNOWN, isLoading = false)
                return@launch
            }
            remoteState.value = remoteState.value.copy(isLoading = true)

            val health = when (walletApiClient.validateToken()) {
                is ApiResult.Success -> TokenHealth.VALID
                is ApiResult.Unauthorized -> TokenHealth.INVALID
                // A token's first use legitimately returns 409 while BudgetBakers builds the
                // account's initial sync - that is "wait", not "broken".
                is ApiResult.SyncInProgress -> TokenHealth.SYNCING
                else -> TokenHealth.UNKNOWN
            }

            val budget = when (val usage = walletApiClient.usageStats()) {
                is ApiResult.Success -> {
                    val limit = usage.data.limit ?: DEFAULT_HOURLY_LIMIT
                    val remaining = usage.data.remaining
                    // The API reports what is LEFT; the card shows what has been USED.
                    RateLimitUiState(used = remaining?.let { (limit - it).coerceAtLeast(0) } ?: 0, limit = limit)
                }
                else -> RateLimitUiState()
            }

            remoteState.value = RemoteState(tokenHealth = health, rateLimit = budget, isLoading = false)
        }
    }

    private data class RemoteState(
        val tokenHealth: TokenHealth = TokenHealth.UNKNOWN,
        val rateLimit: RateLimitUiState = RateLimitUiState(),
        val isLoading: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Documented Wallet API cap, used only when the server has not reported one yet. */
        const val DEFAULT_HOURLY_LIMIT = 300
    }
}
