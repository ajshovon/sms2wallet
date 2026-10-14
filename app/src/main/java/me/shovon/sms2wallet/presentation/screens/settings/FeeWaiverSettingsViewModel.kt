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
import me.shovon.sms2wallet.presentation.model.SupplementaryCardUiState
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

    /**
     * Adds or replaces the tracker for [sourceId], a `"bank|last4"` key from the UI state, with
     * [supplementaryLast4s] counted as part of it.
     */
    fun save(sourceId: String, target: Int, startMonth: Int, startDay: Int, supplementaryLast4s: List<String>) {
        viewModelScope.launch {
            val accountLast4 = sourceId.substringAfter('|', "")
            feeWaiverRepository.save(
                FeeWaiverTracker(
                    bankName = sourceId.substringBefore('|'),
                    accountLast4 = accountLast4,
                    target = target.coerceIn(1, FeeWaiverTracker.MAX_TARGET),
                    startMonth = startMonth,
                    startDay = startDay,
                    supplementaryLast4s = supplementaryLast4s
                        .filter { it.isNotBlank() && it != accountLast4 }
                        .distinct()
                        .sorted(),
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
         * A card already counted somewhere - as a tracked card or as another card's
         * supplementary - is neither offered for tracking nor offered as a supplementary again:
         * either would count its purchases twice.
         *
         * Untracked cards are de-duplicated by label as well as by key: the add sheet lists them
         * by label, and two entries reading the same would be indistinguishable to the user.
         * Both lists sort ignoring case, so "bKash" is not pushed below every capitalised bank.
         */
        internal fun buildFeeWaiverSettings(
            trackers: List<FeeWaiverTracker>,
            sources: List<TransactionSource>,
        ): FeeWaiverSettingsUiState {
            val claimed = trackers.flatMap { tracker -> tracker.allLast4s.map { tracker.bankName to it } }.toSet()
            val seenCards = sources.map { it.bankName to it.accountLast4.orEmpty() }.distinct()

            // Supplementary cards come from the same bank and always have their own last 4 - a
            // source with none is not a separate card the user could have been issued.
            fun supplementaryOptions(bankName: String, mainLast4: String, linked: List<String>) =
                (seenCards.filter { (bank, last4) ->
                    bank == bankName && last4.isNotBlank() && last4 != mainLast4 && (bank to last4) !in claimed
                }.map { it.second } + linked)
                    .distinct()
                    .sorted()
                    .map { SupplementaryCardUiState(last4 = it, label = cardLabel(bankName, it)) }

            // Left out of the options above so they are not counted twice, but named so the
            // editor can say why a card the user can see on the dashboard is not offered.
            fun trackedSeparately(bankName: String, mainLast4: String) = trackers
                .filter { it.bankName == bankName && it.accountLast4.isNotBlank() && it.accountLast4 != mainLast4 }
                .map { cardLabel(it.bankName, it.accountLast4) }
                .sorted()

            val rows = trackers
                .map {
                    it.toSettingsUiState(
                        supplementaryOptions = supplementaryOptions(it.bankName, it.accountLast4, it.supplementaryLast4s),
                        trackedSeparatelyLabels = trackedSeparately(it.bankName, it.accountLast4),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.cardLabel })
            val untracked = seenCards
                .filterNot { it in claimed }
                .map { (bank, last4) ->
                    FeeWaiverCardUiState(
                        sourceId = cardSourceId(bank, last4),
                        label = cardLabel(bank, last4),
                        supplementaryOptions = supplementaryOptions(bank, last4, emptyList()),
                        trackedSeparatelyLabels = trackedSeparately(bank, last4),
                    )
                }
                .distinctBy { it.label }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            return FeeWaiverSettingsUiState(trackers = rows, untrackedCards = untracked, isLoading = false)
        }
    }
}
