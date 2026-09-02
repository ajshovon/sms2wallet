package me.shovon.sms2wallet.presentation.screens.addexpense

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
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.data.repository.WalletSyncRepository
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection

/**
 * Manual cash-entry sheet: spending that never produced an SMS.
 *
 * Account and category options come from the cached Wallet catalogue, so an empty picker means
 * "connect a token and sync" rather than "there are none".
 */
@HiltViewModel
class AddCashExpenseViewModel @Inject constructor(
    private val walletSyncRepository: WalletSyncRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = walletSyncRepository.accounts.first()
            val categories = walletSyncRepository.categories.first()
            _uiState.value = _uiState.value.copy(
                availableAccounts = accounts.map { it.name },
                availableCategories = categories.map { it.name },
                accountName = accounts.firstOrNull()?.name.orEmpty(),
            )
        }
    }

    fun onMerchantChange(value: String) { _uiState.value = _uiState.value.copy(merchant = value) }
    fun onAmountChange(value: String) { _uiState.value = _uiState.value.copy(amountText = value) }
    fun onDirectionChange(value: TransactionDirection) { _uiState.value = _uiState.value.copy(direction = value) }
    fun onCategoryChange(value: String) { _uiState.value = _uiState.value.copy(category = value) }
    fun onAccountChange(value: String) { _uiState.value = _uiState.value.copy(accountName = value) }
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
            _uiState.value = state.copy(errorMessage = "Enter an amount greater than zero.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            val accounts = walletSyncRepository.accounts.first()
            val accountId = accounts.firstOrNull { it.name == state.accountName }?.id
            if (accountId == null) {
                _uiState.value = state.copy(
                    isSaving = false,
                    errorMessage = "Pick a Wallet account - connect your token in Settings if the list is empty.",
                )
                return@launch
            }
            val categories = walletSyncRepository.categories.first()

            transactionRepository.insertManual(
                amount = amount,
                isIncome = state.direction == TransactionDirection.INCOME,
                merchant = state.merchant.takeIf { it.isNotBlank() },
                note = state.note.takeIf { it.isNotBlank() },
                walletAccountId = accountId,
                walletCategoryId = categories.firstOrNull { it.name == state.category }?.id,
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            onDone()
        }
    }
}
