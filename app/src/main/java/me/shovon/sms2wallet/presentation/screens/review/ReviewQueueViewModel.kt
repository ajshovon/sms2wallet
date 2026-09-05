package me.shovon.sms2wallet.presentation.screens.review

import kotlinx.coroutines.flow.first
import me.shovon.bdparser.TransactionType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.local.dao.WalletAccountDao
import me.shovon.sms2wallet.data.local.dao.WalletCategoryDao
import me.shovon.sms2wallet.data.push.PushScheduler
import me.shovon.sms2wallet.data.repository.CategorySubject
import me.shovon.sms2wallet.data.repository.CategorySuggestions
import me.shovon.sms2wallet.data.repository.IntelligenceRepository
import me.shovon.sms2wallet.data.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val walletCategoryDao: WalletCategoryDao,
    private val walletAccountDao: WalletAccountDao,
    private val pushScheduler: PushScheduler,
    private val intelligenceRepository: IntelligenceRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(SelectionState())

    /** Screen-local progress for the bulk suggestion, which has no home in the data layer. */
    private val suggesting = MutableStateFlow(false)

    private val baseState = combine(
        transactionRepository.observeReviewQueue(),
        walletCategoryDao.observeAll(),
        walletAccountDao.observeAll(),
        selection,
        settingsRepository.hasActedOnReviewQueue,
    ) { transactions, categories, accounts, selectionState, hasActed ->
        val categoriesById = categories.associate { it.id to it.name }
        val accountsById = accounts.associate { it.id to it.name }
        val groups = transactions.toReviewQueueGroups(categoriesById, accountsById)
        val visibleIds = transactions.mapTo(mutableSetOf()) { it.id.toString() }
        ReviewQueueUiState(
            groups = groups,
            isMultiSelectMode = selectionState.isMultiSelectMode,
            // Drop ids that have left the queue (approved elsewhere, dismissed on another
            // screen) so the "N selected" count can never exceed what is actually on screen.
            selectedIds = selectionState.selectedIds intersect visibleIds,
            isLoading = false,
            showSwipeHint = !hasActed,
        )
    }

    val uiState: StateFlow<ReviewQueueUiState> = combine(
        baseState,
        suggesting,
        walletCategoryDao.observeAll(),
    ) { base, isSuggesting, categories ->
        // Offered whenever there are categories to assign, not only when a Gemini key exists:
        // learned rules and the built-in merchant table answer for free, and hiding the action
        // without a key would hide functionality that needs no key.
        base.copy(isSuggestingCategories = isSuggesting, isSuggestionAvailable = categories.isNotEmpty())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ReviewQueueUiState(isLoading = true),
    )

    /**
     * Fills in the missing categories across the whole queue in one pass.
     *
     * The batching is the point: rows already answerable from a learned rule or the built-in
     * table never leave the device, the remainder go as a single de-duplicated request, and ten
     * rows from the same shop cost one entry rather than ten.
     *
     * Rows that already have a category are left alone - this fills gaps, it does not overrule
     * decisions the user has already made.
     *
     */
    fun suggestMissingCategories() {
        viewModelScope.launch {
            val rows = transactionRepository.observeReviewQueue().first()
            val needing = rows.filter { it.walletCategoryId.isNullOrBlank() }
            if (needing.isEmpty()) {
                _messages.send("Every transaction already has a category.")
                return@launch
            }

            suggesting.value = true
            val subjects = needing.map { row ->
                CategorySubject(
                    transactionId = row.id,
                    merchant = row.merchant,
                    isIncome = row.type == TransactionType.INCOME.name,
                    bankName = row.bankName,
                )
            }

            when (val result = intelligenceRepository.suggestCategories(subjects)) {
                is CategorySuggestions.Success -> {
                    val note = result.note
                    var applied = 0
                    result.categoryIdByTransactionId.forEach { (id, categoryId) ->
                        val row = needing.firstOrNull { it.id == id } ?: return@forEach
                        transactionRepository.update(
                            row.copy(
                                walletCategoryId = categoryId,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                        applied++
                    }
                    suggesting.value = false
                    val summary = when {
                        applied == 0 -> "No confident matches - pick those by hand."
                        applied == needing.size -> "Filled in $applied categories."
                        else -> "Filled in $applied of ${needing.size}; the rest need a hand."
                    }
                    _messages.send(note?.let { "$summary $it" } ?: summary)
                }

                CategorySuggestions.NotConfigured -> {
                    suggesting.value = false
                    _messages.send("Add a Gemini API key in Settings to use this.")
                }

                is CategorySuggestions.Failure -> {
                    suggesting.value = false
                    _messages.send(result.message)
                }
            }
        }
    }

    /**
     * Approves one row for sending (PARSED/FAILED_RETRYABLE -> QUEUED).
     *
     * [TransactionRepository.approveForSend] is itself state-guarded and returns false without
     * changing anything if the row has already moved on, so a double-tap or a swipe gesture
     * that fires twice cannot queue the same transaction twice.
     */
    fun approve(id: String) {
        val rowId = id.toLongOrNull() ?: return
        viewModelScope.launch {
            // Only schedule when the row actually moved: approveForSend refuses rows that have
            // already left the review states, and waking the worker for those is pointless.
            if (transactionRepository.approveForSend(rowId)) {
                settingsRepository.setHasActedOnReviewQueue()
                pushScheduler.schedule()
            }
        }
    }

    /**
     * Dismisses a row: marks it permanently failed so it leaves the queue but stays on record.
     * Deliberately not a delete - the transaction_hash row is what stops the same SMS being
     * re-ingested on the next inbox scan, so removing it would make the message come back.
     */
    fun dismiss(id: String) {
        val rowId = id.toLongOrNull() ?: return
        viewModelScope.launch {
            if (transactionRepository.dismiss(rowId)) settingsRepository.setHasActedOnReviewQueue()
        }
    }

    /**
     * One-shot user messages (e.g. "Dismissed 12 transactions"). A [Channel] rather than a
     * StateFlow so the message is delivered exactly once and is not replayed when the screen is
     * recreated on rotation.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Dismisses every transaction currently in the review queue.
     *
     * The count comes back from the database rather than from the on-screen list, so the
     * confirmation message stays truthful even if an SMS arrived between the user opening the
     * confirm dialog and confirming it.
     */
    fun dismissAll() {
        viewModelScope.launch {
            val dismissed = transactionRepository.dismissAll()
            selection.value = SelectionState()
            if (dismissed > 0) {
                _messages.send("Dismissed $dismissed ${if (dismissed == 1) "transaction" else "transactions"}")
            }
        }
    }

    /** Dismisses just the rows checked in multi-select mode. */
    fun dismissSelected() {
        val ids = selection.value.selectedIds
        selection.value = SelectionState()
        viewModelScope.launch {
            val dismissed = ids.mapNotNull { it.toLongOrNull() }.count { transactionRepository.dismiss(it) }
            if (dismissed > 0) {
                _messages.send("Dismissed $dismissed ${if (dismissed == 1) "transaction" else "transactions"}")
            }
        }
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
            val approved = ids.mapNotNull { it.toLongOrNull() }.count { transactionRepository.approveForSend(it) }
            if (approved > 0) pushScheduler.schedule()
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
