package me.shovon.sms2wallet.presentation.screens.activity

import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.domain.model.SenderOverride
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.push.PushScheduler
import me.shovon.sms2wallet.data.repository.SmsScanRepository
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
    private val pushScheduler: PushScheduler,
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
        viewModelScope.launch {
            // retryFailed, not approveForSend: a permanently-failed row is exactly what this
            // button exists for, and approveForSend refuses that state.
            if (transactionRepository.retryFailed(transactionId)) pushScheduler.schedule()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** "Unmatched SMS" sub-screen: messages no enabled parser could read. */
@HiltViewModel
class UnmatchedSmsViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val appPreferences: AppPreferences,
    private val smsScanRepository: SmsScanRepository,
) : ViewModel() {

    /** Providers a sender can be pointed at, for the "Assign" picker. */
    val providerNames: List<String> = BankParserFactory.getAllParsers().map { it.getBankName() }

    /**
     * Teaches the app that [sender] belongs to [providerName].
     *
     * The unmatched rows already stored were recorded before the app knew, so they are cleared
     * for that sender: the next inbox scan re-reads those messages and they will parse. Leaving
     * them would show the user a permanent list of messages the app can now handle.
     */
    fun assignSender(sender: String, providerName: String) {
        viewModelScope.launch {
            appPreferences.addSenderOverride(SenderOverride(sender = sender, providerName = providerName))
            activityRepository.deleteUnmatchedBySender(sender)
            // Re-read the inbox so messages that arrived before the app knew this sender are
            // parsed now. Ingest is de-duplicated on a hash of the message, so re-reading
            // cannot produce a second copy of anything already stored.
            smsScanRepository.scanInbox(fromScratch = true)
        }
    }

    init {
        viewModelScope.launch {
            activityRepository.cleanDuplicates()
        }
    }

    val uiState: StateFlow<UnmatchedSmsScreenUiState> = activityRepository.observeUnmatchedSms()
        .map { rows ->
            val distinctRows = rows.distinctBy { "${it.sender.trim()}|${it.body.trim()}" }
            UnmatchedSmsScreenUiState(items = distinctRows.map { it.toUiState() }, isLoading = false)
        }
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
