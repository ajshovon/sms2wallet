package me.shovon.sms2wallet.presentation.screens.addexpense

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
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.push.PushScheduler
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.data.repository.WalletSyncRepository
import me.shovon.sms2wallet.domain.model.WalletLabels
import me.shovon.sms2wallet.domain.model.idFor
import me.shovon.sms2wallet.domain.model.labelFor
import me.shovon.sms2wallet.domain.model.labels
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.navigation.Sms2WalletDestination

/**
 * Manual cash-entry sheet: spending that never produced an SMS, and the landing screen for a
 * transaction parsed from a typed phrase.
 *
 * Account and category options come from the cached Wallet catalogue, so an empty picker means
 * "connect a token and sync" rather than "there are none".
 */
@HiltViewModel
class AddCashExpenseViewModel @Inject constructor(
    private val walletSyncRepository: WalletSyncRepository,
    private val transactionRepository: TransactionRepository,
    private val pushScheduler: PushScheduler,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Values a natural-language parse put in the route, if this screen was opened that way.
     *
     * Read from [SavedStateHandle] rather than passed in, so the prefill is restored after
     * process death exactly as the navigation arguments are.
     */
    private val prefillMerchant: String = savedStateHandle.arg(Sms2WalletDestination.ARG_MERCHANT)
    private val prefillAmount: String = savedStateHandle.arg(Sms2WalletDestination.ARG_AMOUNT)
    private val prefillCategory: String = savedStateHandle.arg(Sms2WalletDestination.ARG_CATEGORY)
    private val prefillAccount: String = savedStateHandle.arg(Sms2WalletDestination.ARG_ACCOUNT)
    private val prefillNote: String = savedStateHandle.arg(Sms2WalletDestination.ARG_NOTE)
    private val prefillIsIncome: Boolean =
        savedStateHandle.arg(Sms2WalletDestination.ARG_INCOME).toBoolean()

    private val _uiState = MutableStateFlow(
        TransactionDetailUiState(
            merchant = prefillMerchant,
            amountText = prefillAmount,
            category = prefillCategory,
            note = prefillNote,
            direction = if (prefillIsIncome) TransactionDirection.INCOME else TransactionDirection.EXPENSE,
        )
    )
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accountLabels = WalletLabels.forAccounts(walletSyncRepository.accounts.first())
            val categoryLabels = WalletLabels.forCategories(walletSyncRepository.categories.first())
            val defaultAccountId = appPreferences.defaultAccountId.first()

            // Precedence: what the phrase named, then the configured default wallet, then
            // whatever is first. The default exists precisely so that a user who does not share
            // account names with the model still lands on the right account.
            val accountLabel = prefillAccount.takeIf { label ->
                accountLabels.idFor(label) != null
            }
                ?: accountLabels.labelFor(defaultAccountId)
                ?: accountLabels.labels().firstOrNull().orEmpty()

            _uiState.value = _uiState.value.copy(
                availableAccounts = accountLabels.labels(),
                availableCategories = categoryLabels.labels(),
                accountName = accountLabel,
            )
        }
    }

    private fun SavedStateHandle.arg(key: String): String = get<String>(key).orEmpty()

    fun onMerchantChange(value: String) { _uiState.value = _uiState.value.copy(merchant = value) }
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
     * Validates and stores the entry, queued for sending.
     *
     * [onDone] runs only after the row is written, so a rejected entry keeps the sheet open with
     * its error rather than closing as though it had been saved.
     */
    fun save(onDone: () -> Unit) {
        val state = _uiState.value

        val amount = runCatching { BigDecimal(state.amountText.trim()) }.getOrNull()
        if (amount == null || amount.signum() <= 0) {
            _uiState.value = state.copy(amountError = "Enter an amount greater than zero.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null, amountError = null, accountError = null)
            val accountLabels = WalletLabels.forAccounts(walletSyncRepository.accounts.first())
            val accountId = accountLabels.idFor(state.accountName)
            if (accountId == null) {
                _uiState.value = state.copy(
                    isSaving = false,
                    accountError = "Pick an account - connect your Wallet token in Settings if the list is empty.",
                )
                return@launch
            }
            val categoryLabels = WalletLabels.forCategories(walletSyncRepository.categories.first())

            transactionRepository.insertManual(
                amount = amount,
                isIncome = state.direction == TransactionDirection.INCOME,
                merchant = state.merchant.takeIf { it.isNotBlank() },
                note = state.note.takeIf { it.isNotBlank() },
                walletAccountId = accountId,
                walletCategoryId = categoryLabels.idFor(state.category),
            )
            pushScheduler.schedule()
            _uiState.value = _uiState.value.copy(isSaving = false)
            onDone()
        }
    }
}
