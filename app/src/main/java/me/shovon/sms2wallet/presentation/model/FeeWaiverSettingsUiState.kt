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
    val label: String
)

/** One tracked card, with what the editor needs to reopen it. */
data class FeeWaiverTrackerUiState(
    val sourceId: String,
    val cardLabel: String,
    val target: Int,
    val startMonth: Int,
    val startDay: Int,
    /** "24 purchases per fee year · starts 15 Mar". */
    val summary: String
)
