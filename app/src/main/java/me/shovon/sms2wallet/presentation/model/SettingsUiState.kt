package me.shovon.sms2wallet.presentation.model

/** UI state for the "Wallet connection" section of Settings. */
data class WalletConnectionUiState(
    val tokenInput: String = "",
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

/** Aggregate UI state for the whole Settings screen. */
data class SettingsUiState(
    val walletConnection: WalletConnectionUiState = WalletConnectionUiState(),
    val parserSettings: List<ParserSettingUiState> = emptyList(),
    val accountMappings: List<AccountMappingRowUiState> = emptyList(),
    val reminders: ReminderSettingsUiState = ReminderSettingsUiState()
)
