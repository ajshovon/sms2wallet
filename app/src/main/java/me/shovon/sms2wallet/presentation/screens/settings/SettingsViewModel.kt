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
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.repository.IntelligenceRepository
import me.shovon.sms2wallet.data.repository.SettingsRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.data.repository.WalletSyncRepository
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.model.AccountMappingRowUiState
import me.shovon.sms2wallet.presentation.model.ConnectionStatus
import me.shovon.sms2wallet.presentation.model.IntelligenceUiState
import me.shovon.sms2wallet.presentation.model.ParserSettingUiState
import me.shovon.sms2wallet.presentation.model.ReminderSettingsUiState
import me.shovon.sms2wallet.presentation.model.SettingsUiState
import me.shovon.sms2wallet.presentation.model.WalletCatalogueUiState
import me.shovon.sms2wallet.presentation.model.WalletConnectionUiState
import me.shovon.sms2wallet.presentation.util.TimeFormatter

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
    private val intelligenceRepository: IntelligenceRepository,
) : ViewModel() {

    /** Screen-local bits with no home in the data layer: what is typed, and the last test result. */
    private val connectionState = MutableStateFlow(WalletConnectionUiState())

    /** Sync progress/error; the counts and timestamp themselves come from Room and DataStore. */
    private val syncState = MutableStateFlow(WalletCatalogueUiState())

    /** Typed key and last test result; the stored key itself never enters UI state. */
    private val intelligenceState = MutableStateFlow(IntelligenceUiState())

    private val baseState: Flow<SettingsUiState> = combine(
        combine(
            settingsRepository.enabledParserNames,
            settingsRepository.autoPushParserNames,
            settingsRepository.observeAccountMappings(),
        ) { enabled, autoPush, mappings -> Triple(enabled, autoPush, mappings) },
        combine(
            transactionRepository.observeDistinctSources(),
            walletSyncRepository.accounts,
            walletSyncRepository.categories,
            settingsRepository.lastCatalogueSyncAt,
            syncState,
        ) { sources, accounts, categories, lastSyncedAt, sync ->
            CatalogueBits(sources, accounts, categories, lastSyncedAt, sync)
        },
        combine(
            settingsRepository.reminderEnabled,
            settingsRepository.reminderTimeMinutes,
            settingsRepository.reminderSuppressThreshold,
            settingsRepository.themeMode,
            settingsRepository.accentColor,
        ) { enabled, timeMinutes, threshold, theme, accent ->
            ReminderBits(enabled, timeMinutes, threshold, theme, accent)
        },
        connectionState,
        settingsRepository.hasToken,
    ) { parserBits, sourceBits, reminderBits, connection, hasToken ->
        val (enabledNames, autoPushNames, mappings) = parserBits
        val sources = sourceBits.sources
        val walletAccounts = sourceBits.accounts
        val reminderEnabled = reminderBits.enabled
        val reminderMinutes = reminderBits.timeMinutes
        val reminderThreshold = reminderBits.threshold

        val accountNames = walletAccounts.map { it.name }
        val mappedNameByBank = mappings.associateBy({ it.bankName }, { it.walletAccountName })

        SettingsUiState(
            // The field is left genuinely empty when a token is stored, and the fact that one
            // exists is carried by hasStoredToken instead. Pre-filling it with a mask made the
            // mask itself editable: typing after it produced a "token" of bullet characters,
            // which the API then rejected with a header-encoding error.
            walletConnection = connection.copy(hasStoredToken = hasToken),
            catalogue = sourceBits.sync.copy(
                accountCount = sourceBits.accounts.size,
                categoryCount = sourceBits.categories.size,
                lastSyncedLabel = TimeFormatter.relativeLabel(sourceBits.lastSyncedAt),
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
            themeMode = reminderBits.themeMode,
            accentColor = reminderBits.accentColor,
            reminders = ReminderSettingsUiState(
                isEnabled = reminderEnabled,
                hourOfDay = reminderMinutes / MINUTES_PER_HOUR,
                minute = reminderMinutes % MINUTES_PER_HOUR,
                skipIfAlreadyLoggedCount = reminderThreshold,
            ),
        )
    }

    // Folded in after the fact for the same reason as on the dashboard: `combine` takes five
    // typed flows, and this section has three sources of its own.
    val uiState: StateFlow<SettingsUiState> = combine(
        baseState,
        intelligenceState,
        intelligenceRepository.settings,
        intelligenceRepository.isConfigured,
        walletSyncRepository.accounts,
    ) { base, local, settings, hasKey, accounts ->
        base.copy(
            intelligence = local.copy(
                hasStoredKey = hasKey,
                model = settings.model,
                shareCategoryNames = settings.shareCategoryNames,
                shareAccountNames = settings.shareAccountNames,
                defaultAccountName = accounts.firstOrNull { it.id == settings.defaultAccountId }?.name,
                availableAccountNames = accounts.map { it.name },
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    // ---- Intelligence ---------------------------------------------------------

    fun onGeminiKeyChange(key: String) {
        intelligenceState.value = intelligenceState.value.copy(
            apiKeyInput = key,
            status = ConnectionStatus.NotTested,
        )
    }

    fun onToggleGeminiKeyVisibility() {
        intelligenceState.value = intelligenceState.value.copy(
            isKeyVisible = !intelligenceState.value.isKeyVisible
        )
    }

    /** Saves the typed key (if any), then checks it and the selected model against Google. */
    fun testGeminiKey() {
        viewModelScope.launch {
            val typed = intelligenceState.value.apiKeyInput.trim()
            if (typed.isNotEmpty()) intelligenceRepository.saveApiKey(typed)

            intelligenceState.value = intelligenceState.value.copy(isTesting = true)
            val error = intelligenceRepository.verifyApiKey()
            intelligenceState.value = intelligenceState.value.copy(
                isTesting = false,
                // Clear the field once it is stored, so a working key is never left sitting
                // in UI state where a screenshot or a state dump could pick it up.
                apiKeyInput = if (error == null) "" else intelligenceState.value.apiKeyInput,
                status = if (error == null) ConnectionStatus.Success else ConnectionStatus.Failed(error),
            )
        }
    }

    fun clearGeminiKey() {
        viewModelScope.launch {
            intelligenceRepository.clearApiKey()
            intelligenceState.value = IntelligenceUiState()
        }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { intelligenceRepository.setModel(model) }
    }

    fun setShareCategoryNames(share: Boolean) {
        viewModelScope.launch { intelligenceRepository.setShareCategoryNames(share) }
    }

    fun setShareAccountNames(share: Boolean) {
        viewModelScope.launch { intelligenceRepository.setShareAccountNames(share) }
    }

    /** [accountName] is resolved to an id here; null clears the default. */
    fun setDefaultAccount(accountName: String?) {
        viewModelScope.launch {
            val id = accountName?.let { name ->
                walletSyncRepository.accounts.first().firstOrNull { it.name == name }?.id
            }
            intelligenceRepository.setDefaultAccountId(id)
        }
    }

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
            val typed = connectionState.value.tokenInput.trim()
            connectionState.value = connectionState.value.copy(isTesting = true)

            // An empty field means "test the token already stored"; anything typed replaces it.
            if (typed.isNotBlank()) settingsRepository.saveToken(typed)

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
                // Clear the field once saved, so the token is not left sitting in UI state.
                connectionState.value = connectionState.value.copy(tokenInput = "")
                walletSyncRepository.refreshAll()
            }
        }
    }

    /**
     * Re-pulls accounts and categories from Wallet.
     *
     * Manual rather than automatic on every screen open: the catalogue changes rarely, and the
     * API allows 300 requests/hour shared with the pushes that actually matter, so silently
     * spending two of them each time Settings is opened would be a poor trade.
     */
    fun syncWalletData() {
        if (syncState.value.isSyncing) return
        viewModelScope.launch {
            syncState.value = syncState.value.copy(isSyncing = true, errorMessage = null)
            val message = when (val result = walletSyncRepository.refreshAll()) {
                is ApiResult.Success -> {
                    // A successful sync is itself proof the token works, so don't leave the
                    // connection status above it contradicting that with "Not tested yet".
                    connectionState.value = connectionState.value.copy(status = ConnectionStatus.Success)
                    null
                }
                is ApiResult.Unauthorized -> {
                    connectionState.value = connectionState.value.copy(
                        status = ConnectionStatus.Failed("Wallet rejected this token (401 Unauthorized)")
                    )
                    "Wallet rejected the saved token. Enter a new one above."
                }
                is ApiResult.SyncInProgress ->
                    "Wallet is still doing its first sync - try again in ${result.retryAfterMinutes ?: DEFAULT_RETRY_MINUTES} minutes."
                is ApiResult.RateLimited -> "Rate limited - try again shortly."
                is ApiResult.NetworkError -> "Couldn't reach Wallet. Check your connection."
                is ApiResult.HttpError -> "Wallet returned an error (${result.status})."
                is ApiResult.InvalidRequest -> result.message
            }
            syncState.value = syncState.value.copy(isSyncing = false, errorMessage = message)
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

    // ---- Appearance -----------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAccentColor(accent: AccentColor) {
        viewModelScope.launch { settingsRepository.setAccentColor(accent) }
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

        const val DEFAULT_RETRY_MINUTES = 5
    }

    /** Groups the reminder + appearance flows so the outer `combine` stays within its arity. */
    private data class ReminderBits(
        val enabled: Boolean,
        val timeMinutes: Int,
        val threshold: Int,
        val themeMode: ThemeMode,
        val accentColor: AccentColor,
    )

    /** Groups the catalogue-related flows so the outer `combine` stays within its arity. */
    private data class CatalogueBits(
        val sources: List<me.shovon.sms2wallet.data.local.dao.TransactionSource>,
        val accounts: List<me.shovon.sms2wallet.data.local.entity.WalletAccountEntity>,
        val categories: List<me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity>,
        val lastSyncedAt: Long,
        val sync: WalletCatalogueUiState,
    )
}
