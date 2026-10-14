package me.shovon.sms2wallet.presentation.screens.bulkentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.push.PushScheduler
import me.shovon.sms2wallet.data.repository.CategorySubject
import me.shovon.sms2wallet.data.repository.CategorySuggestions
import me.shovon.sms2wallet.data.repository.IntelligenceRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.data.repository.WalletSyncRepository
import me.shovon.sms2wallet.domain.model.WalletLabels
import me.shovon.sms2wallet.domain.model.idFor
import me.shovon.sms2wallet.domain.model.labelFor
import me.shovon.sms2wallet.domain.model.labels
import me.shovon.sms2wallet.domain.nlp.BulkEntryParser
import me.shovon.sms2wallet.presentation.model.BulkEntryRowUiState
import me.shovon.sms2wallet.presentation.model.BulkEntryUiState

/**
 * "Log several": reads a typed line into separate entries, fills in what categories it can, and
 * saves the reviewed batch as individual cash transactions queued for Wallet.
 *
 * Reading happens on-device. Only names no learned rule or built-in merchant knows are sent to
 * Gemini - in one request for the whole batch, and only when a key is set.
 */
@HiltViewModel
class BulkEntryViewModel @Inject constructor(
    private val walletSyncRepository: WalletSyncRepository,
    private val transactionRepository: TransactionRepository,
    private val pushScheduler: PushScheduler,
    private val appPreferences: AppPreferences,
    private val intelligenceRepository: IntelligenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkEntryUiState())
    val uiState: StateFlow<BulkEntryUiState> = _uiState.asStateFlow()

    /** The category lookup for the latest read; a newer read cancels an older one still running. */
    private var matchJob: Job? = null

    init {
        viewModelScope.launch {
            val accountLabels = WalletLabels.forAccounts(walletSyncRepository.accounts.first())
            val categoryLabels = WalletLabels.forCategories(walletSyncRepository.categories.first())
            val defaultAccountId = appPreferences.defaultAccountId.first()
            _uiState.update { state ->
                state.copy(
                    availableAccounts = accountLabels.labels(),
                    availableCategories = categoryLabels.labels(),
                    // The same default single cash entries use, so a batch lands where one would.
                    accountName = accountLabels.labelFor(defaultAccountId)
                        ?: accountLabels.labels().firstOrNull().orEmpty(),
                )
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = null) }
    }

    /** Replaces the review list with what the current input reads as. */
    fun read() {
        val rows = BulkEntryParser.parse(_uiState.value.input).mapIndexed { index, entry ->
            BulkEntryRowUiState(
                id = index,
                merchant = entry.merchant,
                amountText = entry.amount?.toPlainString().orEmpty(),
                isIncome = entry.isIncome,
            )
        }
        matchJob?.cancel()
        _uiState.update {
            it.copy(rows = rows, hasRead = true, categoryNote = null, errorMessage = null, isMatchingCategories = false)
        }
        if (rows.isNotEmpty()) matchJob = viewModelScope.launch { matchCategories(rows) }
    }

    private suspend fun matchCategories(rows: List<BulkEntryRowUiState>) {
        val categories = walletSyncRepository.categories.first()
        if (categories.isEmpty()) {
            _uiState.update { it.copy(categoryNote = "Sync your Wallet categories in Settings to fill these in.") }
            return
        }
        _uiState.update { it.copy(isMatchingCategories = true) }

        val subjects = rows.map { row ->
            CategorySubject(
                transactionId = row.id.toLong(),
                merchant = row.merchant.ifBlank { null },
                isIncome = row.isIncome,
                // Learned rules for typed entries are stored without a bank, which this matches.
                bankName = "",
            )
        }
        val result = intelligenceRepository.suggestCategories(subjects, typedByUser = true)
        val (categoryIds, note) = when (result) {
            is CategorySuggestions.Success -> result.categoryIdByTransactionId to result.note
            is CategorySuggestions.Failure -> emptyMap<Long, String>() to result.message
            CategorySuggestions.NotConfigured -> emptyMap<Long, String>() to null
        }
        // Without a key, filling only what is known on-device is the feature working as set up,
        // not a problem to report.
        val showNote = intelligenceRepository.isConfigured.first()
        val labels = WalletLabels.forCategories(categories)

        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    // Never overwrite a category the user picked while the lookup was running.
                    if (row.category.isNotBlank()) row
                    else row.copy(category = labels.labelFor(categoryIds[row.id.toLong()]).orEmpty())
                },
                isMatchingCategories = false,
                categoryNote = note.takeIf { showNote },
            )
        }
    }

    fun onRowMerchantChange(id: Int, value: String) = updateRow(id) { it.copy(merchant = value) }

    fun onRowAmountChange(id: Int, value: String) = updateRow(id) { it.copy(amountText = value) }

    fun onRowCategoryChange(id: Int, label: String) = updateRow(id) { it.copy(category = label) }

    fun onRowRemove(id: Int) {
        _uiState.update { state -> state.copy(rows = state.rows.filterNot { it.id == id }) }
    }

    fun onAccountChange(label: String) {
        _uiState.update { it.copy(accountName = label, errorMessage = null) }
    }

    fun onDateChange(date: LocalDate) {
        // Spending that has not happened yet is not a cash entry.
        _uiState.update { it.copy(date = minOf(date, LocalDate.now())) }
    }

    private fun updateRow(id: Int, change: (BulkEntryRowUiState) -> BulkEntryRowUiState) {
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.id == id) change(it) else it }, errorMessage = null)
        }
    }

    /**
     * Stores every entry as its own cash transaction, queued for Wallet, then calls [onDone] with
     * how many were saved.
     *
     * Each entry gets a timestamp one second apart, ending at the save moment, so they keep the
     * typed order wherever transactions are sorted by time.
     */
    fun save(onDone: (Int) -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            val accountId = WalletLabels.forAccounts(walletSyncRepository.accounts.first()).idFor(state.accountName)
            if (accountId == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Pick an account - connect your Wallet token in Settings if the list is empty.",
                    )
                }
                return@launch
            }
            val categoryLabels = WalletLabels.forCategories(walletSyncRepository.categories.first())
            val entries = state.rows.mapNotNull { row -> row.amount?.let { row to it } }
            val lastTimestamp = timestampFor(state.date)

            entries.forEachIndexed { index, (row, amount) ->
                val merchant = row.merchant.trim().ifBlank { null }
                val categoryId = categoryLabels.idFor(row.category)
                transactionRepository.insertManual(
                    amount = amount,
                    isIncome = row.isIncome,
                    merchant = merchant,
                    note = null,
                    walletAccountId = accountId,
                    walletCategoryId = categoryId,
                    timestamp = lastTimestamp - (entries.size - 1 - index) * ORDER_STEP_MILLIS,
                )
                // Saving is the user's confirmation, the same signal a single cash entry gives.
                intelligenceRepository.rememberCategory(merchant = merchant, bankName = null, walletCategoryId = categoryId)
            }
            pushScheduler.schedule()
            _uiState.update { it.copy(isSaving = false) }
            onDone(entries.size)
        }
    }

    /** Now, for today; the same time of day on an earlier date. */
    private fun timestampFor(date: LocalDate): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val moment = if (date == now.toLocalDate()) now else date.atTime(now.toLocalTime()).atZone(now.zone)
        return moment.toInstant().toEpochMilli()
    }

    private companion object {
        const val ORDER_STEP_MILLIS = 1_000L
    }
}
