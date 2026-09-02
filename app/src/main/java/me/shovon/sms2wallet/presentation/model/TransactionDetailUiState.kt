package me.shovon.sms2wallet.presentation.model

/**
 * UI state shared by the transaction detail/edit sheet (editing a parsed transaction before
 * pushing) and the add-cash-expense sheet (creating a manual transaction). [id] is null for a
 * brand-new manual entry and non-null when editing an existing parsed transaction.
 */
data class TransactionDetailUiState(
    val id: String? = null,
    val merchant: String = "",
    val amountText: String = "",
    val direction: TransactionDirection = TransactionDirection.EXPENSE,
    val category: String = "",
    val availableCategories: List<String> = emptyList(),
    val accountName: String = "",
    val availableAccounts: List<String> = emptyList(),
    val note: String = "",
    val providerName: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val isManualEntry: Boolean get() = id == null
}
