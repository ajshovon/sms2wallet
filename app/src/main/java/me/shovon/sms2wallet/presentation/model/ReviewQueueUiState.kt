package me.shovon.sms2wallet.presentation.model

import java.math.BigDecimal

/**
 * A single parsed-but-unpushed transaction shown as a card in the Review queue.
 *
 * [id] is an opaque UI-layer identifier (a future ViewModel maps it to the real transaction
 * primary key from the data layer).
 */
data class ReviewTransactionUiState(
    val id: String,
    val merchant: String,
    val amount: BigDecimal,
    val direction: TransactionDirection,
    val providerName: String,
    val accountLast4: String,
    val timeLabel: String,
    val category: String? = null,
    val isSuspectedDuplicate: Boolean = false,
    val needsVerification: Boolean = false
)

/** One day-header group in the Review queue list, e.g. "Today", "Yesterday", "Mon, 12 Aug". */
data class ReviewQueueDayGroup(
    val dayLabel: String,
    val transactions: List<ReviewTransactionUiState>
)

/** Aggregate UI state for the Review queue screen. */
data class ReviewQueueUiState(
    val groups: List<ReviewQueueDayGroup> = emptyList(),
    val isMultiSelectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean get() = groups.isEmpty() && !isLoading
    val totalCount: Int get() = groups.sumOf { it.transactions.size }
}
