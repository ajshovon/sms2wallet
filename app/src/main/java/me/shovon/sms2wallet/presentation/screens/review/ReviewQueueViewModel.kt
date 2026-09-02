package me.shovon.sms2wallet.presentation.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.presentation.model.ReviewQueueUiState
import me.shovon.sms2wallet.presentation.model.toReviewQueueGroups

/**
 * Review queue backed by Room.
 *
 * Selection state (multi-select mode, checked ids) is ViewModel-scoped rather than screen-local
 * so it survives tab switches and rotation; the rows themselves always come from the database,
 * so approving or dismissing one is reflected everywhere at once instead of only in this list.
 */
@HiltViewModel
class ReviewQueueViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(SelectionState())

    val uiState: StateFlow<ReviewQueueUiState> = combine(
        transactionRepository.observeReviewQueue(),
        selection,
    ) { transactions, selectionState ->
        val groups = transactions.toReviewQueueGroups()
        val visibleIds = transactions.mapTo(mutableSetOf()) { it.id.toString() }
        ReviewQueueUiState(
            groups = groups,
            isMultiSelectMode = selectionState.isMultiSelectMode,
            // Drop ids that have left the queue (approved elsewhere, dismissed on another
            // screen) so the "N selected" count can never exceed what is actually on screen.
            selectedIds = selectionState.selectedIds intersect visibleIds,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ReviewQueueUiState(isLoading = true),
    )

    /**
     * Approves one row for sending (PARSED/FAILED_RETRYABLE -> QUEUED).
     *
     * [TransactionRepository.approveForSend] is itself state-guarded and returns false without
     * changing anything if the row has already moved on, so a double-tap or a swipe gesture
     * that fires twice cannot queue the same transaction twice.
     */
    fun approve(id: String) {
        val rowId = id.toLongOrNull() ?: return
        viewModelScope.launch { transactionRepository.approveForSend(rowId) }
    }

    /**
     * Dismisses a row: marks it permanently failed so it leaves the queue but stays on record.
     * Deliberately not a delete - the transaction_hash row is what stops the same SMS being
     * re-ingested on the next inbox scan, so removing it would make the message come back.
     */
    fun dismiss(id: String) {
        val rowId = id.toLongOrNull() ?: return
        viewModelScope.launch { transactionRepository.dismiss(rowId) }
    }

    fun toggleMultiSelect() {
        selection.value = SelectionState(isMultiSelectMode = !selection.value.isMultiSelectMode)
    }

    fun setSelected(id: String, selected: Boolean) {
        val current = selection.value
        selection.value = current.copy(
            selectedIds = if (selected) current.selectedIds + id else current.selectedIds - id
        )
    }

    fun approveSelected() {
        val ids = selection.value.selectedIds
        selection.value = SelectionState()
        viewModelScope.launch {
            ids.mapNotNull { it.toLongOrNull() }.forEach { transactionRepository.approveForSend(it) }
        }
    }

    private data class SelectionState(
        val isMultiSelectMode: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
