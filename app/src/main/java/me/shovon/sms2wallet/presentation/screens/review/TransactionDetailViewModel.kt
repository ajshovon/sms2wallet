package me.shovon.sms2wallet.presentation.screens.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.domain.model.WalletLabels
import me.shovon.sms2wallet.domain.model.idFor
import me.shovon.sms2wallet.domain.model.labelFor
import me.shovon.sms2wallet.domain.model.labels
import me.shovon.sms2wallet.data.push.PushScheduler
import me.shovon.sms2wallet.data.repository.CategorySubject
import me.shovon.sms2wallet.data.repository.CategorySuggestions
import me.shovon.sms2wallet.data.repository.IntelligenceRepository
import me.shovon.sms2wallet.data.repository.SettingsRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.domain.category.MerchantCategoryGuesser
import me.shovon.sms2wallet.data.repository.WalletSyncRepository
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.model.toDetailUiState
import me.shovon.sms2wallet.presentation.navigation.Sms2WalletDestination

/**
 * Edit sheet for one stored transaction, loaded by the route's `transactionId`.
 *
 * Edits are held locally and written back only on [save], so backing out of the sheet discards
 * them rather than silently mutating a row the user was only looking at.
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val walletSyncRepository: WalletSyncRepository,
    private val settingsRepository: SettingsRepository,
    private val pushScheduler: PushScheduler,
    private val intelligenceRepository: IntelligenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val transactionId: Long? =
        savedStateHandle.get<String>(Sms2WalletDestination.ARG_TRANSACTION_ID)?.toLongOrNull()

    private val _uiState = MutableStateFlow(TransactionDetailUiState(isSaving = true))
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val entity = transactionId?.let { transactionRepository.findById(it) }
        if (entity == null) {
            _uiState.value = TransactionDetailUiState(
                isSaving = false,
                errorMessage = "This transaction no longer exists.",
            )
            return
        }

        val accounts = walletSyncRepository.accounts.first()
        val categories = walletSyncRepository.categories.first()

        // Fall back to the account mapping when the row itself carries none. A transaction
        // ingested before the user mapped its provider was stored without an account; without
        // this it would open with an empty picker even though a mapping now exists.
        val accountId = entity.walletAccountId
            ?: settingsRepository.findMappingFor(entity.bankName, entity.accountLast4)?.walletAccountId

        // Same for the category: guess from the merchant when nothing is stored, so the common
        // case is one tap (confirm) rather than two (choose, then confirm).
        val categoryId = entity.walletCategoryId
            ?: MerchantCategoryGuesser.guess(entity.merchant, categories)

        val accountLabels = WalletLabels.forAccounts(accounts)
        val categoryLabels = WalletLabels.forCategories(categories)
        // Offered whenever there are categories to assign, matching the queue's bulk action:
        // a learned rule or the built-in merchant table answers for free, so gating this on a
        // Gemini key would hide a suggestion that needs no key at all.
        val suggestionAvailable = categories.isNotEmpty()

        _uiState.value = entity.toDetailUiState(
            availableAccounts = accountLabels.labels(),
            availableCategories = categoryLabels.labels(),
            accountName = accountLabels.labelFor(accountId).orEmpty(),
            categoryName = categoryLabels.labelFor(categoryId).orEmpty(),
        ).copy(isSaving = false, isSuggestionAvailable = suggestionAvailable)
    }

    fun onMerchantChange(value: String) { _uiState.value = _uiState.value.copy(merchant = value) }
    // Clearing the field's error as soon as the user edits it keeps a stale complaint from
    // sitting under a value they have already corrected.
    fun onAmountChange(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value, amountError = null)
    }
    fun onDirectionChange(value: TransactionDirection) { _uiState.value = _uiState.value.copy(direction = value) }
    fun onCategoryChange(value: String) { _uiState.value = _uiState.value.copy(category = value) }
    fun onAccountChange(value: String) {
        _uiState.value = _uiState.value.copy(accountName = value, accountError = null)
    }
    fun onNoteChange(value: String) { _uiState.value = _uiState.value.copy(note = value) }

    /**
     * Persists the edits and approves the row for sending.
     *
     * [onDone] runs only on success, so a rejected save leaves the sheet open with its error
     * showing rather than navigating away as though the edit had been kept.
     */
    fun save(onDone: () -> Unit) {
        val id = transactionId ?: return
        val state = _uiState.value

        val amount = parsePositiveAmount(state.amountText)
        if (amount == null) {
            _uiState.value = state.copy(amountError = "Enter an amount greater than zero.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null, amountError = null, accountError = null)

            val existing = transactionRepository.findById(id)
            if (existing == null) {
                _uiState.value = state.copy(isSaving = false, errorMessage = "This transaction no longer exists.")
                return@launch
            }

            val accountLabels = WalletLabels.forAccounts(walletSyncRepository.accounts.first())
            val accountId = accountLabels.idFor(state.accountName)
            if (accountId == null) {
                // Queuing a row with no Wallet account would put it in a state the send pipeline
                // can never resolve, so refuse here where the user can still fix it.
                _uiState.value = state.copy(
                    isSaving = false,
                    accountError = "Pick an account - there is nowhere to push this yet.",
                )
                return@launch
            }
            val categoryLabels = WalletLabels.forCategories(walletSyncRepository.categories.first())

            transactionRepository.update(
                existing.copy(
                    merchant = state.merchant.takeIf { it.isNotBlank() },
                    amount = amount.toPlainString(),
                    type = if (state.direction == TransactionDirection.INCOME) {
                        TransactionType.INCOME.name
                    } else {
                        TransactionType.EXPENSE.name
                    },
                    reference = state.note.takeIf { it.isNotBlank() },
                    walletAccountId = accountId,
                    walletCategoryId = categoryLabels.idFor(state.category),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            // Learn the merchant -> category pairing from the confirmation. This is the point
            // the user vouched for it, so the next transaction from this shop is answered
            // on-device with no API call.
            intelligenceRepository.rememberCategory(
                merchant = state.merchant.takeIf { it.isNotBlank() },
                bankName = existing.bankName,
                walletCategoryId = categoryLabels.idFor(state.category),
            )
            if (transactionRepository.approveForSend(id)) pushScheduler.schedule()
            _uiState.value = _uiState.value.copy(isSaving = false)
            onDone()
        }
    }

    /**
     * Fills the category field with a suggestion, leaving it alone when there is none.
     *
     * Only ever offered, never applied silently on open: the user asked for this one, and a
     * field that fills itself while being read is harder to trust than one that waits.
     */
    fun suggestCategory() {
        val id = transactionId ?: return
        viewModelScope.launch {
            val entity = transactionRepository.findById(id) ?: return@launch
            _uiState.value = _uiState.value.copy(isSuggestingCategory = true, suggestionMessage = null)

            val subject = CategorySubject(
                transactionId = id,
                merchant = _uiState.value.merchant.takeIf { it.isNotBlank() } ?: entity.merchant,
                isIncome = _uiState.value.direction == TransactionDirection.INCOME,
                bankName = entity.bankName,
            )

            when (val result = intelligenceRepository.suggestCategories(listOf(subject))) {
                is CategorySuggestions.Success -> {
                    val categoryId = result.categoryIdByTransactionId[id]
                    val categories = walletSyncRepository.categories.first()
                    val label = WalletLabels.forCategories(categories).labelFor(categoryId)
                    _uiState.value = _uiState.value.copy(
                        category = label ?: _uiState.value.category,
                        isSuggestingCategory = false,
                        suggestionMessage = if (label == null) {
                            "No confident match - pick one below."
                        } else {
                            null
                        },
                    )
                }

                CategorySuggestions.NotConfigured -> _uiState.value = _uiState.value.copy(
                    isSuggestingCategory = false,
                    suggestionMessage = "Add a Gemini API key in Settings to use this.",
                )

                is CategorySuggestions.Failure -> _uiState.value = _uiState.value.copy(
                    isSuggestingCategory = false,
                    suggestionMessage = result.message,
                )
            }
        }
    }

    /**
     * Dismisses this transaction from the review queue without pushing it.
     *
     * Offered here as well as by swiping the queue row, because the review sheet is exactly
     * where a user decides a parsed transaction is not worth keeping - making them back out and
     * swipe instead would be a dead end.
     */
    fun dismiss(onDone: () -> Unit) {
        val id = transactionId ?: return
        viewModelScope.launch {
            transactionRepository.dismiss(id)
            onDone()
        }
    }

    /** Amount is stored unsigned; direction carries the sign, so anything <= 0 is a typo. */
    private fun parsePositiveAmount(text: String): BigDecimal? =
        runCatching { BigDecimal(text.trim()) }.getOrNull()?.takeIf { it.signum() > 0 }
}
