package me.shovon.sms2wallet.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.data.local.entity.AccountMappingEntity
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.WalletApiClient
import me.shovon.sms2wallet.data.repository.SettingsRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.data.repository.WalletSyncRepository
import me.shovon.sms2wallet.presentation.model.AccountMappingRowUiState
import me.shovon.sms2wallet.presentation.model.ConnectionStatus
import me.shovon.sms2wallet.presentation.model.ParserSettingUiState
import me.shovon.sms2wallet.presentation.model.ReminderSettingsUiState
import me.shovon.sms2wallet.presentation.model.SettingsUiState
import me.shovon.sms2wallet.presentation.model.WalletConnectionUiState

/**
 * Settings backed by DataStore, the Keystore-encrypted token store, and Room.
 *
 * The plaintext Wallet token is never held in observed UI state: [SettingsRepository] only
 * exposes `hasToken`, and what the text field shows is either what the user is currently typing
 * or a fixed placeholder standing in for "something is stored". A stored token is therefore
 * never readable off the screen, and never ends up in a state dump or a saved-state bundle.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val walletSyncRepository: WalletSyncRepository,
    private val transactionRepository: TransactionRepository,
    private val walletApiClient: WalletApiClient,
) : ViewModel() {

    /** Screen-local bits with no home in the data layer: what is typed, and the last test result. */
    private val connectionState = MutableStateFlow(WalletConnectionUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.enabledParserNames,
            settingsRepository.autoPushParserNames,
            settingsRepository.observeAccountMappings(),
        ) { enabled, autoPush, mappings -> Triple(enabled, autoPush, mappings) },
        combine(
            transactionRepository.observeDistinctSources(),
            walletSyncRepository.accounts,
        ) { sources, accounts -> sources to accounts },
        combine(
            settingsRepository.reminderEnabled,
            settingsRepository.reminderTimeMinutes,
            settingsRepository.reminderSuppressThreshold,
        ) { enabled, timeMinutes, threshold -> Triple(enabled, timeMinutes, threshold) },
        connectionState,
        settingsRepository.hasToken,
    ) { parserBits, sourceBits, reminderBits, connection, hasToken ->
        val (enabledNames, autoPushNames, mappings) = parserBits
        val (sources, walletAccounts) = sourceBits
        val (reminderEnabled, reminderMinutes, reminderThreshold) = reminderBits

        val accountNames = walletAccounts.map { it.name }
        val mappedNameByBank = mappings.associateBy({ it.bankName }, { it.walletAccountName })

        SettingsUiState(
            walletConnection = connection.copy(
                // Show a stand-in for a stored token rather than the token itself. An empty
                // field with a token saved would read as "not connected"; the real value must
                // never leave the Keystore-backed store.
                tokenInput = if (connection.tokenInput.isEmpty() && hasToken) STORED_TOKEN_PLACEHOLDER
                else connection.tokenInput,
            ),
            parserSettings = BankParserFactory.getAllParsers().map { parser ->
                val name = parser.getBankName()
                ParserSettingUiState(
                    providerName = name,
                    isEnabled = name in enabledNames,
                    isAutoPushEnabled = name in autoPushNames,
                    mappedAccountName = mappedNameByBank[name],
                )
            },
            accountMappings = buildAccountMappingRows(sources, mappings, accountNames),
            reminders = ReminderSettingsUiState(
                isEnabled = reminderEnabled,
                hourOfDay = reminderMinutes / MINUTES_PER_HOUR,
                minute = reminderMinutes % MINUTES_PER_HOUR,
                skipIfAlreadyLoggedCount = reminderThreshold,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    /**
     * One row per source the app has actually seen an SMS from, union the sources already
     * mapped. Without the union, a mapping whose source has since been purged would silently
     * vanish from the screen while still routing transactions.
     */
    private fun buildAccountMappingRows(
        sources: List<me.shovon.sms2wallet.data.local.dao.TransactionSource>,
        mappings: List<AccountMappingEntity>,
        accountNames: List<String>,
    ): List<AccountMappingRowUiState> {
        val fromTransactions = sources.map { it.bankName to (it.accountLast4 ?: AccountMappingEntity.UNKNOWN_LAST4) }
        val fromMappings = mappings.map { it.bankName to it.accountLast4 }
        val mappedNameBySource = mappings.associateBy(
            { it.bankName to it.accountLast4 },
            { it.walletAccountName },
        )
        return (fromTransactions + fromMappings).distinct().sortedBy { it.first }.map { (bank, last4) ->
            AccountMappingRowUiState(
                sourceId = "$bank|$last4",
                sourceLabel = if (last4.isBlank()) bank else "$bank •••• $last4",
                mappedWalletAccountName = mappedNameBySource[bank to last4],
                availableWalletAccountNames = accountNames,
            )
        }
    }

    // ---- Wallet connection ----------------------------------------------------

    fun onTokenChange(token: String) {
        connectionState.value = connectionState.value.copy(
            tokenInput = token,
            // Any edit invalidates the previous result - showing a stale "Connected" against a
            // token the user has since changed would be actively misleading.
            status = ConnectionStatus.NotTested,
        )
    }

    fun onToggleTokenVisibility() {
        connectionState.value = connectionState.value.copy(
            isTokenVisible = !connectionState.value.isTokenVisible
        )
    }

    /**
     * Saves the typed token (if any) and makes a real `validateToken` call against the Wallet API.
     */
    fun testConnection() {
        viewModelScope.launch {
            val typed = connectionState.value.tokenInput
            connectionState.value = connectionState.value.copy(isTesting = true)

            // The placeholder means "keep whatever is stored"; only a genuinely new value is saved.
            if (typed.isNotBlank() && typed != STORED_TOKEN_PLACEHOLDER) {
                settingsRepository.saveToken(typed.trim())
            }

            val status = when (val result = walletApiClient.validateToken()) {
                is ApiResult.Success -> ConnectionStatus.Success
                is ApiResult.Unauthorized -> ConnectionStatus.Failed("Wallet rejected this token (401 Unauthorized)")
                is ApiResult.SyncInProgress ->
                    ConnectionStatus.Syncing(retryInMinutes = result.retryAfterMinutes ?: DEFAULT_RETRY_MINUTES)
                is ApiResult.RateLimited ->
                    ConnectionStatus.Failed("Rate limited - try again in ${result.retryAfterSeconds ?: 0} seconds")
                is ApiResult.HttpError -> ConnectionStatus.Failed("Wallet API error ${result.status}: ${result.message.orEmpty()}")
                is ApiResult.NetworkError -> ConnectionStatus.Failed("Network error: ${result.message.orEmpty()}")
                is ApiResult.InvalidRequest -> ConnectionStatus.Failed(result.message)
            }

            connectionState.value = connectionState.value.copy(isTesting = false, status = status)

            // A valid token is the first moment the account/category pickers can be populated.
            if (status is ConnectionStatus.Success) {
                walletSyncRepository.refreshAccounts()
                walletSyncRepository.refreshCategories()
            }
        }
    }

    // ---- Parsers --------------------------------------------------------------

    fun setParserEnabled(providerName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.enabledParserNames.first()
            settingsRepository.setEnabledParserNames(
                if (enabled) current + providerName else current - providerName
            )
            // A disabled parser must not keep auto-pushing; clear the dependent switch too.
            if (!enabled) {
                val autoPush = settingsRepository.autoPushParserNames.first()
                if (providerName in autoPush) {
                    settingsRepository.setAutoPushParserNames(autoPush - providerName)
                }
            }
        }
    }

    fun setParserAutoPush(providerName: String, autoPush: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.autoPushParserNames.first()
            settingsRepository.setAutoPushParserNames(
                if (autoPush) current + providerName else current - providerName
            )
        }
    }

    // ---- Account mapping ------------------------------------------------------

    /** [sourceId] is the `"bank|last4"` key built in [buildAccountMappingRows]. */
    fun setAccountMapping(sourceId: String, walletAccountName: String) {
        viewModelScope.launch {
            val bankName = sourceId.substringBefore('|')
            val last4 = sourceId.substringAfter('|', AccountMappingEntity.UNKNOWN_LAST4)
            val account = walletSyncRepository.accounts.first().firstOrNull { it.name == walletAccountName }
                ?: return@launch
            val existing = settingsRepository.observeAccountMappings().first()
                .firstOrNull { it.bankName == bankName && it.accountLast4 == last4 }
            settingsRepository.upsertAccountMapping(
                AccountMappingEntity(
                    id = existing?.id ?: 0,
                    bankName = bankName,
                    accountLast4 = last4,
                    walletAccountId = account.id,
                    walletAccountName = account.name,
                    autoPush = existing?.autoPush ?: false,
                    defaultCategoryId = existing?.defaultCategoryId,
                )
            )
        }
    }

    // ---- Reminders ------------------------------------------------------------

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReminderEnabled(enabled) }
    }

    fun setReminderTime(hourOfDay: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderTimeMinutes(hourOfDay * MINUTES_PER_HOUR + minute)
        }
    }

    fun setReminderSkipCount(count: Int) {
        viewModelScope.launch { settingsRepository.setReminderSuppressThreshold(count) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MINUTES_PER_HOUR = 60

        /** Stands in for an already-stored token; never the real value. */
        const val STORED_TOKEN_PLACEHOLDER = "••••••••••••••••"

        const val DEFAULT_RETRY_MINUTES = 5
    }
}
