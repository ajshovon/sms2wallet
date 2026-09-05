package me.shovon.sms2wallet.presentation.model

import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.IntelligenceSettings
import me.shovon.sms2wallet.domain.model.ThemeMode

/** UI state for the "Wallet connection" section of Settings. */
data class WalletConnectionUiState(
    /** What the user is currently typing. Empty means "keep whatever is already stored". */
    val tokenInput: String = "",
    /** True once a token is saved on the device. Never carries the token itself. */
    val hasStoredToken: Boolean = false,
    val isTokenVisible: Boolean = false,
    val status: ConnectionStatus = ConnectionStatus.NotTested,
    val isTesting: Boolean = false
)

/**
 * Result of testing the BudgetBakers Wallet API token. [Syncing] is a real, expected API
 * response the first time a token is used - it must render as an informational/neutral state,
 * not an error.
 */
sealed interface ConnectionStatus {
    data object NotTested : ConnectionStatus
    data object Success : ConnectionStatus
    data class Syncing(val retryInMinutes: Int) : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

/**
 * One row in the "Parsers" section: a provider (e.g. bKash) with two independent switches.
 * Auto-push has no effect unless [mappedAccountName] is non-null - the UI must make that
 * dependency explicit.
 */
data class ParserSettingUiState(
    val providerName: String,
    val isEnabled: Boolean = true,
    val isAutoPushEnabled: Boolean = false,
    val mappedAccountName: String? = null
) {
    val isMapped: Boolean get() = mappedAccountName != null
}

/** One row in the "Account mapping" section: a detected SMS source mapped to a Wallet account. */
data class AccountMappingRowUiState(
    val sourceId: String,
    val sourceLabel: String,
    val mappedWalletAccountName: String? = null,
    val availableWalletAccountNames: List<String> = emptyList()
)

/** UI state for the "Reminders" section. */
data class ReminderSettingsUiState(
    val isEnabled: Boolean = false,
    val hourOfDay: Int = 21,
    val minute: Int = 0,
    val skipIfAlreadyLoggedCount: Int = 3
)

/**
 * State of the locally cached Wallet catalogue (accounts + categories).
 *
 * The app caches these so pickers stay fast, work offline and don't spend the hourly request
 * budget on every sheet. The trade-off is that anything created in Wallet after the last sync
 * is invisible here, so this state exists to make the staleness visible and fixable.
 */
data class WalletCatalogueUiState(
    val accountCount: Int = 0,
    val categoryCount: Int = 0,
    /** Relative label, e.g. "2 minutes ago"; null when never synced. */
    val lastSyncedLabel: String? = null,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * UI state for the "Intelligence" section: the Gemini key, and exactly what is shared with it.
 *
 * Like [WalletConnectionUiState], the stored key is never held here - only [hasStoredKey]. The
 * sharing switches are state the user reads to answer "what is leaving my phone?", so they are
 * driven by the persisted settings rather than by anything transient.
 */
data class IntelligenceUiState(
    val apiKeyInput: String = "",
    val hasStoredKey: Boolean = false,
    val isKeyVisible: Boolean = false,
    val isTesting: Boolean = false,
    val status: ConnectionStatus = ConnectionStatus.NotTested,
    val model: String = IntelligenceSettings.DEFAULT_MODEL,
    val modelOptions: List<String> = IntelligenceSettings.MODEL_OPTIONS,
    val shareCategoryNames: Boolean = true,
    val shareAccountNames: Boolean = false,
    val shareMerchantNames: Boolean = false,
    /** Null when no default is set, in which case the first cached account is used. */
    val defaultAccountName: String? = null,
    val availableAccountNames: List<String> = emptyList()
)

/** Aggregate UI state for the whole Settings screen. */
data class SettingsUiState(
    val walletConnection: WalletConnectionUiState = WalletConnectionUiState(),
    val catalogue: WalletCatalogueUiState = WalletCatalogueUiState(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DYNAMIC,
    val parserSettings: List<ParserSettingUiState> = emptyList(),
    val accountMappings: List<AccountMappingRowUiState> = emptyList(),
    val reminders: ReminderSettingsUiState = ReminderSettingsUiState(),
    val intelligence: IntelligenceUiState = IntelligenceUiState(),
    val learnedCategories: List<LearnedCategoryUiState> = emptyList()
)

/**
 * One merchant->category pairing the app has learned from a confirmed transaction.
 *
 * Surfaced so the learning is inspectable and reversible: a rule that quietly files every
 * future transaction from a shop is only trustworthy if the user can see and delete it.
 */
data class LearnedCategoryUiState(
    val id: Long,
    val keyword: String,
    val categoryLabel: String
)
