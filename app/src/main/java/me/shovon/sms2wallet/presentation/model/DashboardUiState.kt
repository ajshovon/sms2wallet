package me.shovon.sms2wallet.presentation.model

/**
 * UI state for the Dashboard tab. A future ViewModel populates this from the real
 * repository/data layer; for now screens are driven by sample data.
 */
data class DashboardUiState(
    val pushedToday: Int = 0,
    val pushedThisWeek: Int = 0,
    val pendingReviewCount: Int = 0,
    val lastSyncLabel: String? = null,
    val tokenHealth: TokenHealth = TokenHealth.UNKNOWN,
    val rateLimit: RateLimitUiState = RateLimitUiState(),
    val quickAdd: QuickAddUiState = QuickAddUiState(),
    val feeWaivers: List<FeeWaiverProgressUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

/**
 * One tracked card on the dashboard: purchases so far this fee year against the number its
 * issuer needs to waive the annual fee.
 */
data class FeeWaiverProgressUiState(
    val cardLabel: String,
    val count: Int,
    val target: Int,
    /** "10 more by 14 Mar 2027 · 151 days left", or "Target reached · resets 15 Mar 2027". */
    val statusLabel: String,
    /** "Includes supplementary •••• 5678" when other cards count towards this one; else null. */
    val supplementaryLabel: String? = null,
) {
    val isMet: Boolean get() = count >= target

    val fraction: Float
        get() = if (target <= 0) 1f else (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

/**
 * The "type it" box at the top of the dashboard.
 *
 * [isAvailable] is false until a Gemini API key is stored. The field is hidden rather than
 * disabled in that state: a permanently greyed-out control on the home screen is clutter that
 * explains nothing, and the feature is opt-in by design.
 */
data class QuickAddUiState(
    val isAvailable: Boolean = false,
    val input: String = "",
    val isParsing: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean get() = input.isNotBlank() && !isParsing
}

/** Health of the stored BudgetBakers Wallet API token, surfaced on the dashboard. */
enum class TokenHealth {
    UNKNOWN,
    VALID,
    EXPIRING_SOON,
    INVALID,
    SYNCING
}

/**
 * Rate-limit budget for the Wallet API (documented cap: 300 requests/hour).
 * [used] and [limit] drive a progress-style indicator.
 */
data class RateLimitUiState(
    val used: Int = 0,
    val limit: Int = 300,
    val windowLabel: String = "this hour"
) {
    val fraction: Float
        get() = if (limit <= 0) 0f else (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}
