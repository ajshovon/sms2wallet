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
    val accountName: String? = null,
    val isSuspectedDuplicate: Boolean = false,
    val needsVerification: Boolean = false
) {
    /** True when this row is flagged for a reason the user has to look at before pushing. */
    val needsAttention: Boolean get() = isSuspectedDuplicate || needsVerification || accountName == null

    /**
     * The one-line provenance caption: "bKash •••• 1234 • 9:02 AM", collapsing to
     * "bKash • 9:02 AM" when the SMS exposed no account digits - a masked placeholder for an
     * unknown account reads as real data the app does not actually have.
     */
    val sourceSummary: String
        get() = buildString {
            append(providerName)
            if (accountLast4.isNotBlank() && accountLast4.none { it == '-' }) {
                append(" •••• ")
                append(accountLast4)
            }
            append(" • ")
            append(timeLabel)
        }
}

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
    val isLoading: Boolean = false,
    /** True until the user has pushed or dismissed once; drives the one-time swipe hint. */
    val showSwipeHint: Boolean = false,
    /** True once a Gemini key is stored, which gates the bulk-suggest action. */
    val isSuggestionAvailable: Boolean = false,
    /** True while a batch category suggestion is in flight. */
    val isSuggestingCategories: Boolean = false
) {
    val isEmpty: Boolean get() = groups.isEmpty() && !isLoading
    val totalCount: Int get() = groups.sumOf { it.transactions.size }

    /** All rows across every day group, flattened - for counting and bulk actions. */
    val allTransactions: List<ReviewTransactionUiState>
        get() = groups.flatMap { it.transactions }

    /** How many rows carry a duplicate/verification flag, for the "needs attention" summary. */
    val attentionCount: Int get() = allTransactions.count { it.needsAttention }
}
