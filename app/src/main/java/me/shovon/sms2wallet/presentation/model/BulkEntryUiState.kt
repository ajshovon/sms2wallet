package me.shovon.sms2wallet.presentation.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * "Log several": a day's cash spending typed as one line, read into separate entries the user
 * checks before they are saved.
 */
data class BulkEntryUiState(
    val input: String = "",
    val rows: List<BulkEntryRowUiState> = emptyList(),
    /** True once the input has been read, so an empty result can say why rather than show nothing. */
    val hasRead: Boolean = false,
    val isMatchingCategories: Boolean = false,
    /** Why some categories were left blank, when that is worth saying. */
    val categoryNote: String? = null,
    val accountName: String = "",
    val availableAccounts: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    /** Every entry in the batch is dated this day. */
    val date: LocalDate = LocalDate.now(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Rows still missing a usable amount; the batch cannot be saved until there are none. */
    val invalidCount: Int get() = rows.count { it.amount == null }

    val canSave: Boolean
        get() = rows.isNotEmpty() && invalidCount == 0 && accountName.isNotBlank() &&
            !isSaving && !isMatchingCategories

    val totalSpent: BigDecimal
        get() = rows.filter { !it.isIncome }.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)

    val totalReceived: BigDecimal
        get() = rows.filter { it.isIncome }.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
}

/** One entry in the review list. [category] is a catalogue label, or empty for none. */
data class BulkEntryRowUiState(
    val id: Int,
    val merchant: String,
    val amountText: String,
    val isIncome: Boolean,
    val category: String = "",
) {
    /** Null until the amount field holds a number above zero. */
    val amount: BigDecimal?
        get() = amountText.trim().replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
}
