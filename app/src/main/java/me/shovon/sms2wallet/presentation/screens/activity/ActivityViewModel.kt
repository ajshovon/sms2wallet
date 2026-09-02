package me.shovon.sms2wallet.presentation.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.repository.ActivityRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.presentation.model.ActivityUiState
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsScreenUiState
import me.shovon.sms2wallet.presentation.model.toUiState

/** Activity tab: the push audit log, newest first. */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<ActivityUiState> = activityRepository.observeRecentPushLog()
        .map { logs -> ActivityUiState(logs = logs.map { it.toUiState() }, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ActivityUiState(isLoading = true),
        )

    /**
     * Requeues a failed transaction. The log row's id is not the transaction's, so this takes
     * the transaction id the row was built from; rows whose transaction is gone are not
     * retryable and never reach here.
     */
    fun retry(transactionId: Long) {
        viewModelScope.launch { transactionRepository.approveForSend(transactionId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** "Unmatched SMS" sub-screen: messages no enabled parser could read. */
@HiltViewModel
class UnmatchedSmsViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
) : ViewModel() {

    val uiState: StateFlow<UnmatchedSmsScreenUiState> = activityRepository.observeUnmatchedSms()
        .map { rows -> UnmatchedSmsScreenUiState(items = rows.map { it.toUiState() }, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UnmatchedSmsScreenUiState(isLoading = true),
        )

    fun dismiss(id: String) {
        val rowId = id.toLongOrNull() ?: return
        viewModelScope.launch { activityRepository.deleteUnmatchedSms(rowId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
