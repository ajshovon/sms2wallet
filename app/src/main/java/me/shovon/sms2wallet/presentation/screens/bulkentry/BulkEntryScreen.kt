package me.shovon.sms2wallet.presentation.screens.bulkentry

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.PickerControl
import me.shovon.sms2wallet.presentation.components.PickerField
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.BulkEntryRowUiState
import me.shovon.sms2wallet.presentation.model.BulkEntryUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.util.MoneyFormatter

/**
 * "Log several": type the day's cash spending in one go, check the entries it reads as, and save
 * them all. Nothing is stored until Save - each row can be corrected or removed first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEntryScreen(
    state: BulkEntryUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onRead: () -> Unit,
    onRowMerchantChange: (id: Int, value: String) -> Unit,
    onRowAmountChange: (id: Int, value: String) -> Unit,
    onRowCategoryChange: (id: Int, label: String) -> Unit,
    onRowRemove: (id: Int) -> Unit,
    onAccountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var pickingDate by remember { mutableStateOf(false) }

    Sms2WalletScaffold(
        title = "Log several",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(SolarIcons.ArrowBack, contentDescription = "Back")
            }
        },
        bottomBar = {
            if (state.rows.isNotEmpty()) {
                SaveBar(
                    state = state,
                    onSave = {
                        keyboardController?.hide()
                        onSave()
                    }
                )
            }
        }
    ) { padding ->
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
            item(key = "input") {
                AppTextField(
                    label = "What did you spend?",
                    value = state.input,
                    onValueChange = onInputChange,
                    placeholder = "uber 120, tea 20, cig 10",
                    supportingText = "Separate items with commas or new lines. Start one with + for money in.",
                    singleLine = false
                )
            }

            item(key = "read") {
                FilledTonalButton(
                    onClick = {
                        keyboardController?.hide()
                        onRead()
                    },
                    enabled = state.input.isNotBlank() && !state.isSaving,
                    modifier = Modifier.padding(top = Spacing.xs)
                ) {
                    Text(if (state.rows.isEmpty()) "Read entries" else "Read again")
                }
            }

            if (state.hasRead && state.rows.isEmpty()) {
                item(key = "nothing") {
                    Text(
                        text = "Nothing to add yet. Write each item with its amount, e.g. \"tea 20\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.lg)
                    )
                }
            }

            if (state.rows.isNotEmpty()) {
                item(key = "entries-header") {
                    SectionHeader(
                        title = if (state.rows.size == 1) "1 entry" else "${state.rows.size} entries",
                        supportingText = buildList {
                            add("${MoneyFormatter.formatBdt(state.totalSpent)} spent")
                            if (state.totalReceived.signum() > 0) add("${MoneyFormatter.formatBdt(state.totalReceived)} in")
                        }.joinToString(" · "),
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                }

                item(key = "batch") {
                    GroupedContainer(modifier = Modifier.padding(bottom = Spacing.md)) {
                        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                            PickerField(
                                label = "Account",
                                value = state.accountName.ifBlank { null },
                                options = state.availableAccounts,
                                onSelect = onAccountChange,
                                placeholder = "Choose an account",
                                sheetTitle = "Account for every entry"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Date", style = MaterialTheme.typography.bodyLarge)
                                TextButton(
                                    onClick = { pickingDate = true },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Date for every entry: ${dateLabel(state.date)}. Change"
                                    }
                                ) {
                                    Text(dateLabel(state.date))
                                }
                            }
                        }
                    }
                }

                if (state.isMatchingCategories || state.categoryNote != null) {
                    item(key = "category-status") {
                        CategoryStatus(
                            isMatching = state.isMatchingCategories,
                            note = state.categoryNote
                        )
                    }
                }

                items(state.rows, key = { it.id }) { row ->
                    EntryCard(
                        row = row,
                        categories = state.availableCategories,
                        onMerchantChange = { onRowMerchantChange(row.id, it) },
                        onAmountChange = { onRowAmountChange(row.id, it) },
                        onCategoryChange = { onRowCategoryChange(row.id, it) },
                        onRemove = { onRowRemove(row.id) },
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }
            }
        }
    }

    if (pickingDate) {
        val today = LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toUtcMillis(),
            // Spending that has not happened yet is not a cash entry.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today.toUtcMillis()
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports midnight UTC of the chosen day, whatever the device zone.
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
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

/** One entry: name and amount side by side, its category underneath. */
@Composable
private fun EntryCard(
    row: BulkEntryRowUiState,
    categories: List<String>,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = row.merchant.ifBlank { "entry" }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = groupedSurfaceColor()
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = row.merchant,
                    onValueChange = onMerchantChange,
                    placeholder = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = row.amountText,
                    onValueChange = onAmountChange,
                    placeholder = { Text("0") },
                    prefix = { Text(if (row.isIncome) "+৳" else "৳") },
                    singleLine = true,
                    isError = row.amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .width(AMOUNT_FIELD_WIDTH)
                        .semantics { contentDescription = "Amount for $name" }
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = SolarIcons.Close,
                        contentDescription = "Remove $name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
            }
            Row(
                modifier = Modifier.padding(end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PickerControl(
                    value = row.category.ifBlank { null },
                    options = categories,
                    onSelect = onCategoryChange,
                    placeholder = "Category",
                    sheetTitle = "Category for $name",
                    searchPlaceholder = "Search categories",
                    contentDescription = "Category for $name",
                    modifier = Modifier.weight(1f)
                )
                if (row.isIncome) {
                    StatusBadge(text = "Money in", intent = BadgeIntent.INFO)
                }
            }
            if (row.amount == null) {
                Text(
                    text = "Add an amount above zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Says when categories are still being looked up, and why any were left blank. */
@Composable
private fun CategoryStatus(isMatching: Boolean, note: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (isMatching) {
            CircularProgressIndicator(modifier = Modifier.size(IconSize.sm), strokeWidth = 2.dp)
        }
        Text(
            text = if (isMatching) "Matching categories…" else note.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Sticky save button, worded with what will actually be saved. */
@Composable
private fun SaveBar(state: BulkEntryUiState, onSave: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SAVE_BUTTON_HEIGHT),
                shape = MaterialTheme.shapes.large
            ) {
                if (state.isSaving) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {
                    Text(
                        text = when {
                            // Save waits for the lookup (up to Gemini's 30s timeout) so a batch is
                            // not stored a moment before its categories arrive - say so on the
                            // button, which may be the only part of the screen still in view.
                            state.isMatchingCategories -> "Matching categories…"
                            state.invalidCount == 1 -> "Fix 1 amount to save"
                            state.invalidCount > 1 -> "Fix ${state.invalidCount} amounts to save"
                            state.accountName.isBlank() -> "Choose an account to save"
                            state.rows.size == 1 -> "Save 1 entry"
                            else -> "Save ${state.rows.size} entries"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val DAY_LABEL_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DAY_LABEL)
        else -> date.format(DAY_LABEL_WITH_YEAR)
    }
}

private val AMOUNT_FIELD_WIDTH = 124.dp
private val SAVE_BUTTON_HEIGHT = 54.dp
