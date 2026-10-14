package me.shovon.sms2wallet.presentation.model

/** The "Card fee waivers" settings screen: cards being tracked, and cards that could be. */
data class FeeWaiverSettingsUiState(
    val trackers: List<FeeWaiverTrackerUiState> = emptyList(),
    /** Cards seen in SMS that are not tracked yet - what "Track a card" offers. */
    val untrackedCards: List<FeeWaiverCardUiState> = emptyList(),
    val isLoading: Boolean = true
)

/** A card that can be tracked, keyed by its `"bank|last4"` source id. */
data class FeeWaiverCardUiState(
    val sourceId: String,
    val label: String,
    /** Cards from the same bank that could be linked to this one as supplementary cards. */
    val supplementaryOptions: List<SupplementaryCardUiState> = emptyList(),
    /** Same-bank cards tracked on their own, which must be untracked before they can be linked. */
    val trackedSeparatelyLabels: List<String> = emptyList()
)

/** One tracked card, with what the editor needs to reopen it. */
data class FeeWaiverTrackerUiState(
    val sourceId: String,
    val cardLabel: String,
    val target: Int,
    val startMonth: Int,
    val startDay: Int,
    /** "24 purchases per fee year · starts 15 Mar · +1 supplementary". */
    val summary: String,
    val supplementaryLast4s: List<String> = emptyList(),
    /** The cards linked now plus any others from the same bank that are free to link. */
    val supplementaryOptions: List<SupplementaryCardUiState> = emptyList(),
    /** Same-bank cards tracked on their own, which must be untracked before they can be linked. */
    val trackedSeparatelyLabels: List<String> = emptyList()
)

/** A card from the same bank whose purchases can count towards the main card's waiver. */
data class SupplementaryCardUiState(
    val last4: String,
    val label: String
)
