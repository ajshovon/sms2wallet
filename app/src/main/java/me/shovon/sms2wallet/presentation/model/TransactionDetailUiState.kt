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
    /** "bKash •••• 1234 • Today, 9:02 AM" - the provenance line on the review header. */
    val sourceSummary: String? = null,
    /** The original SMS text, so the user can check the parse against the source. */
    val smsPreview: String? = null,
    val isSuspectedDuplicate: Boolean = false,
    val needsVerification: Boolean = false,
    val isSaving: Boolean = false,
    /** True once a Gemini key is stored, which is what gates the suggest affordance. */
    val isSuggestionAvailable: Boolean = false,
    /** True while a category suggestion is in flight. */
    val isSuggestingCategory: Boolean = false,
    /** Why a suggestion produced nothing, or null when there is nothing to say. */
    val suggestionMessage: String? = null,
    /** Form-level failure shown as a summary above the form (e.g. "this row no longer exists"). */
    val errorMessage: String? = null,
    /** Field-level errors, rendered inline beneath the field they belong to. */
    val amountError: String? = null,
    val accountError: String? = null
) {
    val isManualEntry: Boolean get() = id == null

    /** True when this transaction carries a flag the user should resolve before pushing. */
    val needsAttention: Boolean get() = isSuspectedDuplicate || needsVerification

    /** True when nothing anywhere on the form is currently in error. */
    val isValid: Boolean get() = errorMessage == null && amountError == null && accountError == null
}
