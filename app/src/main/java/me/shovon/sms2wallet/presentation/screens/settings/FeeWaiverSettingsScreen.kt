package me.shovon.sms2wallet.presentation.screens.settings

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.SectionDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.SelectionSheet
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.FeeWaiverSettingsUiState
import me.shovon.sms2wallet.presentation.model.FeeWaiverTrackerUiState
import me.shovon.sms2wallet.presentation.model.SupplementaryCardUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MinTouchTarget
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * Settings → Card fee waivers: pick the cards whose annual fee is waived after enough purchases,
 * and say how many and from what date. A supplementary card is linked to its main card here
 * rather than tracked alone. The dashboard then shows each card's progress.
 */
@Composable
fun FeeWaiverSettingsScreen(
    state: FeeWaiverSettingsUiState,
    onBack: () -> Unit,
    onSave: (sourceId: String, target: Int, startMonth: Int, startDay: Int, supplementaryLast4s: List<String>) -> Unit,
    onRemove: (sourceId: String) -> Unit
) {
    var addSheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditorTarget?>(null) }

    Sms2WalletScaffold(
        title = "Card fee waivers",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(SolarIcons.ArrowBack, contentDescription = "Back")
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Sms2WalletScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl
            )
        ) {
            item(key = "intro") {
                Text(
                    text = "Some credit cards waive their annual fee once you make enough purchases " +
                        "in a fee year. Track a card here and the dashboard shows how many you have left.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )
            }

            item(key = "tracked-header") {
                SectionHeader(
                    title = "Tracked cards",
                    trailing = if (state.trackers.isNotEmpty() && state.untrackedCards.isNotEmpty()) {
                        {
                            TextButton(onClick = { addSheetOpen = true }) {
                                Icon(
                                    imageVector = SolarIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.md)
                                )
                                Spacer(Modifier.size(Spacing.xs))
                                Text("Add")
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            item(key = "tracked-body") {
                GroupedContainer {
                    when {
                        state.trackers.isNotEmpty() -> state.trackers.forEachIndexed { index, tracker ->
                            TrackerRow(
                                tracker = tracker,
                                onEdit = { editing = EditorTarget.from(tracker) },
                                onRemove = { onRemove(tracker.sourceId) }
                            )
                            if (index < state.trackers.lastIndex) SectionDivider()
                        }

                        state.untrackedCards.isEmpty() -> Text(
                            text = "No cards detected yet. Once an SMS from your card arrives, it can be tracked here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.lg)
                        )

                        else -> Column(
                            modifier = Modifier.padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Text(
                                text = "No cards tracked yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(onClick = { addSheetOpen = true }) {
                                Icon(
                                    imageVector = SolarIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.sm)
                                )
                                Spacer(Modifier.size(Spacing.xs))
                                Text("Track a card")
                            }
                        }
                    }
                }
            }

            item(key = "footnote") {
                Text(
                    text = "Only purchases count - bill payments, refunds and cashback on the card do not. " +
                        "Counts come from the SMS on this phone, so a purchase whose SMS never arrived " +
                        "or was deleted is missing from them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }
        }
    }

    if (addSheetOpen) {
        SelectionSheet(
            title = "Track a card",
            options = state.untrackedCards.map { it.label },
            selected = null,
            searchPlaceholder = "Search cards",
            onSelect = { label ->
                addSheetOpen = false
                state.untrackedCards.firstOrNull { it.label == label }?.let { card ->
                    editing = EditorTarget(
                        sourceId = card.sourceId,
                        cardLabel = card.label,
                        target = FeeWaiverTracker.DEFAULT_TARGET,
                        // The calendar year is the most common fee year, and the editor puts the
                        // date right in front of the user to change.
                        startMonth = 1,
                        startDay = 1,
                        supplementaryLast4s = emptyList(),
                        supplementaryOptions = card.supplementaryOptions,
                        trackedSeparatelyLabels = card.trackedSeparatelyLabels
                    )
                }
            },
            onDismiss = { addSheetOpen = false }
        )
    }

    editing?.let { target ->
        TrackerEditorDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = { count, month, day, supplementary ->
                onSave(target.sourceId, count, month, day, supplementary)
                editing = null
            }
        )
    }
}

/** One tracked card. Tapping the row edits it; the trailing button stops tracking it. */
@Composable
private fun TrackerRow(
    tracker: FeeWaiverTrackerUiState,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit", onClick = onEdit)
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tracker.cardLabel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tracker.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
        }
        // No confirmation: removing forgets a target and a date, not any transactions, and
        // re-adding the card brings the same count straight back.
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = SolarIcons.Close,
                contentDescription = "Stop tracking ${tracker.cardLabel}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.md)
            )
        }
    }
}

/**
 * Target, fee-year start and supplementary cards for one card.
 *
 * The date picker offers a full date because that is how a card anniversary is remembered, but
 * only the day and month are kept: the year renews by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerEditorDialog(
    initial: EditorTarget,
    onDismiss: () -> Unit,
    onSave: (target: Int, startMonth: Int, startDay: Int, supplementaryLast4s: List<String>) -> Unit
) {
    var targetText by remember(initial) { mutableStateOf(initial.target.toString()) }
    var supplementary by remember(initial) { mutableStateOf(initial.supplementaryLast4s.toSet()) }
    var startMonth by remember(initial) { mutableIntStateOf(initial.startMonth) }
    var startDay by remember(initial) { mutableIntStateOf(initial.startDay) }
    var pickingDate by remember { mutableStateOf(false) }
    val target = targetText.toIntOrNull()?.takeIf { it in 1..FeeWaiverTracker.MAX_TARGET }
    val startLabel = MonthDay.of(startMonth, startDay).format(DAY_MONTH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.cardLabel) },
        text = {
            // Scrolls so a bank with several cards cannot push Save off a small screen.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                AppTextField(
                    label = "Purchases needed",
                    value = targetText,
                    onValueChange = { input -> targetText = input.filter(Char::isDigit).take(TARGET_MAX_DIGITS) },
                    supportingText = "Per fee year, as your bank's waiver terms state.",
                    errorText = if (target == null) "Enter a number from 1 to ${FeeWaiverTracker.MAX_TARGET}" else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fee year starts",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(
                        onClick = { pickingDate = true },
                        modifier = Modifier.semantics { contentDescription = "Fee year starts $startLabel. Change" }
                    ) {
                        Text(startLabel)
                    }
                }
                Text(
                    text = "Counting restarts on this date every year - usually your card's anniversary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SupplementaryCardsSection(
                    options = initial.supplementaryOptions,
                    trackedSeparately = initial.trackedSeparatelyLabels,
                    selected = supplementary,
                    onToggle = { last4, linked ->
                        supplementary = if (linked) supplementary + last4 else supplementary - last4
                    },
                    showEmptyHint = initial.hasCardNumber
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onSave(it, startMonth, startDay, supplementary.sorted()) } },
                enabled = target != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (pickingDate) {
        // Opens on the start of the current fee year, so the calendar shows a real date the user
        // recognises rather than an arbitrary year.
        val currentStart = FeeWaiverTracker.feeYearContaining(LocalDate.now(), startMonth, startDay).start
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports midnight UTC of the chosen day, whatever the device zone.
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        startMonth = picked.monthValue
                        startDay = picked.dayOfMonth
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Other cards from the same bank, ticked when they are supplementary cards of this one.
 *
 * Shown only when there is something to link. Otherwise it says why nothing is offered: either
 * the other card is [trackedSeparately] and has to be untracked first, or no SMS from a second
 * card has arrived yet - so the user knows what to do rather than assume the feature is missing.
 */
@Composable
private fun SupplementaryCardsSection(
    options: List<SupplementaryCardUiState>,
    trackedSeparately: List<String>,
    selected: Set<String>,
    onToggle: (last4: String, linked: Boolean) -> Unit,
    showEmptyHint: Boolean
) {
    val trackedSeparatelyNote = trackedSeparately.takeIf { it.isNotEmpty() }?.let { labels ->
        val subject = labels.joinToString(" and ")
        if (labels.size == 1) {
            "$subject is tracked on its own. Stop tracking it to link it here as a supplementary card."
        } else {
            "$subject are tracked on their own. Stop tracking one to link it here as a supplementary card."
        }
    }

    if (options.isEmpty()) {
        if (trackedSeparatelyNote != null) {
            Text(
                text = trackedSeparatelyNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        } else if (showEmptyHint) {
            Text(
                text = "A supplementary card can be linked here once an SMS from it arrives.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
        return
    }

    Text(
        text = "Supplementary cards",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    Text(
        text = "Purchases on a ticked card count towards this card's target.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    options.forEach { option ->
        val isLinked = option.last4 in selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(
                    value = isLinked,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(option.last4, it) }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Checkbox(checked = isLinked, onCheckedChange = null)
            Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
    trackedSeparatelyNote?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** What the editor opens with: an existing tracker's values, or defaults for a new card. */
private data class EditorTarget(
    val sourceId: String,
    val cardLabel: String,
    val target: Int,
    val startMonth: Int,
    val startDay: Int,
    val supplementaryLast4s: List<String>,
    val supplementaryOptions: List<SupplementaryCardUiState>,
    val trackedSeparatelyLabels: List<String>
) {
    /** A source with no last 4 (most mobile wallets) has no supplementary cards to link. */
    val hasCardNumber: Boolean get() = sourceId.substringAfter('|', "").isNotBlank()

    companion object {
        fun from(tracker: FeeWaiverTrackerUiState) = EditorTarget(
            sourceId = tracker.sourceId,
            cardLabel = tracker.cardLabel,
            target = tracker.target,
            startMonth = tracker.startMonth,
            startDay = tracker.startDay,
            supplementaryLast4s = tracker.supplementaryLast4s,
            supplementaryOptions = tracker.supplementaryOptions,
            trackedSeparatelyLabels = tracker.trackedSeparatelyLabels
        )
    }
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

private const val TARGET_MAX_DIGITS = 3
