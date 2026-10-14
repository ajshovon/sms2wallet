package me.shovon.sms2wallet.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.sms2wallet.data.repository.FeeWaiverRepository
import me.shovon.sms2wallet.data.repository.TransactionRepository
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import me.shovon.sms2wallet.presentation.model.FeeWaiverCardUiState
import me.shovon.sms2wallet.presentation.model.FeeWaiverSettingsUiState
import me.shovon.sms2wallet.presentation.model.cardLabel
import me.shovon.sms2wallet.presentation.model.cardSourceId
import me.shovon.sms2wallet.presentation.model.toSettingsUiState

/** Settings → Card fee waivers: which cards are tracked, their targets and fee-year start. */
@HiltViewModel
class FeeWaiverSettingsViewModel @Inject constructor(
    private val feeWaiverRepository: FeeWaiverRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<FeeWaiverSettingsUiState> = combine(
        feeWaiverRepository.trackers,
        transactionRepository.observeCardSources(),
    ) { trackers, sources -> buildFeeWaiverSettings(trackers, sources) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FeeWaiverSettingsUiState(),
        )

    /** Adds or replaces the tracker for [sourceId], a `"bank|last4"` key from the UI state. */
    fun save(sourceId: String, target: Int, startMonth: Int, startDay: Int) {
        viewModelScope.launch {
            feeWaiverRepository.save(
                FeeWaiverTracker(
                    bankName = sourceId.substringBefore('|'),
                    accountLast4 = sourceId.substringAfter('|', ""),
                    target = target.coerceIn(1, FeeWaiverTracker.MAX_TARGET),
                    startMonth = startMonth,
                    startDay = startDay,
                )
            )
        }
    }

    fun remove(sourceId: String) {
        viewModelScope.launch {
            feeWaiverRepository.remove(
                bankName = sourceId.substringBefore('|'),
                accountLast4 = sourceId.substringAfter('|', ""),
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Tracked cards, plus every card seen in SMS that is not tracked yet.
         *
         * Untracked cards are de-duplicated by label as well as by key: the add sheet lists them
         * by label, and two entries reading the same would be indistinguishable to the user.
         * Both lists sort ignoring case, so "bKash" is not pushed below every capitalised bank.
         */
        internal fun buildFeeWaiverSettings(
            trackers: List<FeeWaiverTracker>,
            sources: List<TransactionSource>,
        ): FeeWaiverSettingsUiState {
            val rows = trackers.map { it.toSettingsUiState() }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.cardLabel })
            val tracked = rows.map { it.sourceId }.toSet()
            val untracked = sources
                .map { FeeWaiverCardUiState(cardSourceId(it.bankName, it.accountLast4), cardLabel(it.bankName, it.accountLast4)) }
                .filterNot { it.sourceId in tracked }
                .distinctBy { it.sourceId }
                .distinctBy { it.label }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            return FeeWaiverSettingsUiState(trackers = rows, untrackedCards = untracked, isLoading = false)
        }
    }
}
