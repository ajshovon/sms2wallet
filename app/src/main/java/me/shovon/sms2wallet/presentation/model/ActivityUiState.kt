package me.shovon.sms2wallet.presentation.model

import java.math.BigDecimal

/** Outcome of one push attempt shown in the Activity log. */
enum class PushLogStatus {
    SUCCESS,
    FAILED,
    PENDING,
    RETRYING
}

/**
 * One row in the push log.
 *
 * [id] identifies the *log* row (there can be several per transaction, one per attempt), while
 * [transactionId] is what a retry has to act on. It is null when the underlying transaction has
 * since been removed, which is exactly when the row is no longer retryable.
 */
data class PushLogEntryUiState(
    val id: String,
    val transactionId: Long? = null,
    val merchant: String,
    val amount: BigDecimal,
    val direction: TransactionDirection,
    val status: PushLogStatus,
    val timeLabel: String,
    val errorMessage: String? = null
) {
    val isRetryable: Boolean get() = status == PushLogStatus.FAILED && transactionId != null
}

/** UI state for the Activity tab (push log). */
data class ActivityUiState(
    val logs: List<PushLogEntryUiState> = emptyList(),
    val isLoading: Boolean = false
)

/** A raw SMS that no parser could match, shown in the "Unmatched SMS" sub-screen. */
data class UnmatchedSmsUiState(
    val id: String,
    val sender: String,
    val bodyPreview: String,
    val receivedAtLabel: String
)

/** UI state for the "Unmatched SMS" sub-screen. */
data class UnmatchedSmsScreenUiState(
    val items: List<UnmatchedSmsUiState> = emptyList(),
    val isLoading: Boolean = false
)
