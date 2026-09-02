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
import me.shovon.sms2wallet.data.repository.TransactionRepository
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

        _uiState.value = entity.toDetailUiState(
            availableAccounts = accounts.map { it.name },
            availableCategories = categories.map { it.name },
            accountName = accounts.firstOrNull { it.id == entity.walletAccountId }?.name.orEmpty(),
            categoryName = categories.firstOrNull { it.id == entity.walletCategoryId }?.name.orEmpty(),
        ).copy(isSaving = false)
    }

    fun onMerchantChange(value: String) { _uiState.value = _uiState.value.copy(merchant = value) }
    fun onAmountChange(value: String) { _uiState.value = _uiState.value.copy(amountText = value) }
    fun onDirectionChange(value: TransactionDirection) { _uiState.value = _uiState.value.copy(direction = value) }
    fun onCategoryChange(value: String) { _uiState.value = _uiState.value.copy(category = value) }
    fun onAccountChange(value: String) { _uiState.value = _uiState.value.copy(accountName = value) }
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
            _uiState.value = state.copy(errorMessage = "Enter an amount greater than zero.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)

            val existing = transactionRepository.findById(id)
            if (existing == null) {
                _uiState.value = state.copy(isSaving = false, errorMessage = "This transaction no longer exists.")
                return@launch
            }

            val accounts = walletSyncRepository.accounts.first()
            val accountId = accounts.firstOrNull { it.name == state.accountName }?.id
            if (accountId == null) {
                // Queuing a row with no Wallet account would put it in a state the send pipeline
                // can never resolve, so refuse here where the user can still fix it.
                _uiState.value = state.copy(
                    isSaving = false,
                    errorMessage = "Pick a Wallet account first - there is nowhere to push this yet.",
                )
                return@launch
            }
            val categories = walletSyncRepository.categories.first()

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
                    walletCategoryId = categories.firstOrNull { it.name == state.category }?.id,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            transactionRepository.approveForSend(id)
            _uiState.value = _uiState.value.copy(isSaving = false)
            onDone()
        }
    }

    /** Amount is stored unsigned; direction carries the sign, so anything <= 0 is a typo. */
    private fun parsePositiveAmount(text: String): BigDecimal? =
        runCatching { BigDecimal(text.trim()) }.getOrNull()?.takeIf { it.signum() > 0 }
}
