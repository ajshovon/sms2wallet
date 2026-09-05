package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ReviewQueueUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MotionDuration
import me.shovon.sms2wallet.presentation.theme.StandardEasing
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.domain.model.AccentColor

/**
 * Review queue tab: parsed-but-unpushed transactions grouped by day. Swipe right to push,
 * swipe left to dismiss, tap to open the full review sheet, or use multi-select to act in bulk.
 *
 * Fully stateless and hoisted - all state comes from [ReviewQueueViewModel], so it survives tab
 * switches and rotation instead of resetting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueContent(
    state: ReviewQueueUiState,
    onOpenTransaction: (String) -> Unit,
    onPush: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onToggleSelected: (String, Boolean) -> Unit,
    onBulkPush: () -> Unit,
    onBulkDismiss: () -> Unit,
    onDismissAll: () -> Unit,
    onSuggestCategories: () -> Unit = {},
    snackbarHostState: androidx.compose.material3.SnackbarHostState? = null
) {
    var confirmDismissAll by remember { mutableStateOf(false) }

    Sms2WalletScaffold(
        title = if (state.isMultiSelectMode) "${state.selectedIds.size} selected" else "Review",
        snackbarHostState = snackbarHostState,
        navigationIcon = {
            if (state.isMultiSelectMode) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(SolarIcons.ArrowBack, contentDescription = "Exit selection")
                }
            }
        },
        actions = {
            if (state.isMultiSelectMode) {
                IconButton(onClick = onBulkDismiss, enabled = state.selectedIds.isNotEmpty()) {
                    Icon(SolarIcons.DeleteSweep, contentDescription = "Dismiss selected")
                }
                IconButton(onClick = onBulkPush, enabled = state.selectedIds.isNotEmpty()) {
                    Icon(SolarIcons.PublishedWithChanges, contentDescription = "Push selected")
                }
            } else if (!state.isEmpty) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(SolarIcons.Checklist, contentDescription = "Select multiple")
                }
                QueueOverflowMenu(
                    onDismissAllClick = { confirmDismissAll = true },
                    canSuggestCategories = state.isSuggestionAvailable,
                    isSuggestingCategories = state.isSuggestingCategories,
                    onSuggestCategoriesClick = onSuggestCategories
                )
            }
        }
    ) { padding ->
        // While the first emission is still in flight the queue is neither empty nor populated.
        // Rendering the list branch here flashed "0 transactions to review" before the real
        // count arrived, which reads as "nothing found" rather than "still loading".
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

        if (state.isEmpty) {
            EmptyState(
                icon = SolarIcons.CheckCircle,
                title = "All caught up",
                description = "New transactions parsed from your SMS show up here so you can check them before they reach Wallet.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Sms2WalletScaffold
        }

        var filterAttentionOnly by remember { mutableStateOf(false) }
        val visibleGroups = remember(state.groups, filterAttentionOnly) {
            if (!filterAttentionOnly) state.groups
            else state.groups.mapNotNull { group ->
                val filtered = group.transactions.filter { it.needsAttention }
                if (filtered.isEmpty()) null else group.copy(transactions = filtered)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.lg
            )
        ) {
            item(key = "summary") {
                QueueSummary(
                    total = state.totalCount,
                    attentionCount = state.attentionCount,
                    showSwipeHint = state.showSwipeHint,
                    filterAttentionOnly = filterAttentionOnly,
                    onToggleFilterAttention = { filterAttentionOnly = !filterAttentionOnly },
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }

            visibleGroups.forEach { group ->
                item(key = "header-${group.dayLabel}") {
                    SectionHeader(
                        title = group.dayLabel,
                        modifier = Modifier.padding(top = Spacing.lg)
                    )
                }
                itemsIndexed(
                    items = group.transactions,
                    key = { _, transaction -> transaction.id }
                ) { index, transaction ->
                    if (index > 0) GroupedRowDivider()
                    TransactionCard(
                        transaction = transaction,
                        modifier = Modifier.animateItem(
                            placementSpec = tween(
                                durationMillis = MotionDuration.EMPHASISED_MILLIS,
                                easing = StandardEasing
                            )
                        ),
                        index = index,
                        count = group.transactions.size,
                        onClick = {
                            if (state.isMultiSelectMode) {
                                onToggleSelected(transaction.id, transaction.id !in state.selectedIds)
                            } else {
                                onOpenTransaction(transaction.id)
                            }
                        },
                        onPush = { onPush(transaction.id) },
                        onDismiss = { onDismiss(transaction.id) },
                        isMultiSelectMode = state.isMultiSelectMode,
                        isSelected = transaction.id in state.selectedIds,
                        onSelectedChange = { selected -> onToggleSelected(transaction.id, selected) }
                    )
                }
            }

            if (visibleGroups.isEmpty() && filterAttentionOnly) {
                item(key = "empty-attention-filter") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = SolarIcons.CheckCircle,
                            contentDescription = null,
                            tint = Sms2WalletTheme.extendedColors.income,
                            modifier = Modifier.size(IconSize.xl)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = "No transactions need attention",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "All remaining transactions have verified accounts and categories.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (confirmDismissAll) {
        val haptics = LocalHapticFeedback.current
        DismissAllDialog(
            count = state.totalCount,
            onConfirm = {
                confirmDismissAll = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismissAll()
            },
            onCancel = { confirmDismissAll = false }
        )
    }
}

/**
 * Overflow menu holding the destructive bulk action.
 *
 * "Dismiss all" lives behind an overflow rather than as a top-level icon on purpose: it is
 * irreversible and applies to everything on screen, so it should take one deliberate extra tap
 * instead of sitting next to the per-row actions where it can be hit by accident.
 */
@Composable
private fun QueueOverflowMenu(
    onDismissAllClick: () -> Unit,
    canSuggestCategories: Boolean,
    isSuggestingCategories: Boolean,
    onSuggestCategoriesClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    IconButton(onClick = { menuOpen = true }) {
        Icon(SolarIcons.MoreVert, contentDescription = "More actions")
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        // Bulk suggestion sits above the destructive item and is separated from it: the two
        // read similarly in a list ("do something to everything") but only one is irreversible.
        if (canSuggestCategories) {
            DropdownMenuItem(
                text = {
                    Text(if (isSuggestingCategories) "Suggesting…" else "Suggest missing categories")
                },
                enabled = !isSuggestingCategories,
                leadingIcon = {
                    Icon(
                        imageVector = SolarIcons.Science,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.lg)
                    )
                },
                onClick = {
                    menuOpen = false
                    onSuggestCategoriesClick()
                }
            )
            HorizontalDivider()
        }

        DropdownMenuItem(
            text = { Text("Dismiss all", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    imageVector = SolarIcons.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(IconSize.lg)
                )
            },
            onClick = {
                menuOpen = false
                onDismissAllClick()
            }
        )
    }
}

/** Confirmation for the irreversible bulk dismiss. Names the exact count so it is never vague. */
@Composable
private fun DismissAllDialog(count: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val subject = if (count == 1) "1 transaction" else "all $count transactions"
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = SolarIcons.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Dismiss $subject?") },
        text = {
            Text(
                "They will be removed from the review queue and never pushed to Wallet. " +
                    "This can't be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                // Names the action, not "OK": the button text alone should say what happens.
                Text(
                    text = if (count == 1) "Dismiss" else "Dismiss all",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/**
 * The at-a-glance answer to "what am I looking at, and does any of it need me?".
 *
 * Sits above the list so the user reads the shape of the queue before its contents, and the
 * attention line only appears when something is actually flagged - a permanent "0 need
 * attention" row would be noise on the common, healthy case.
 */
@Composable
private fun QueueSummary(
    total: Int,
    attentionCount: Int,
    showSwipeHint: Boolean,
    filterAttentionOnly: Boolean,
    onToggleFilterAttention: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (total == 1) "1 transaction to review" else "$total transactions to review",
            style = MaterialTheme.typography.headlineSmall
        )
        // Retired for good once the gesture has been used: an instruction that never leaves is
        // furniture, not help. It fades rather than vanishing so the list does not jump.
        AnimatedVisibility(
            visible = showSwipeHint,
            enter = fadeIn(tween(MotionDuration.STANDARD_MILLIS, easing = StandardEasing)),
            exit = fadeOut(tween(MotionDuration.QUICK_MILLIS)) +
                shrinkVertically(tween(MotionDuration.STANDARD_MILLIS, easing = StandardEasing))
        ) {
            Text(
                text = "Swipe right to push, left to dismiss, or tap to edit first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }

        AnimatedVisibility(
            visible = attentionCount > 0,
            enter = fadeIn(tween(MotionDuration.STANDARD_MILLIS, easing = StandardEasing)) +
                expandVertically(tween(MotionDuration.STANDARD_MILLIS, easing = StandardEasing)),
            exit = fadeOut(tween(MotionDuration.QUICK_MILLIS)) +
                shrinkVertically(tween(MotionDuration.QUICK_MILLIS))
        ) {
            Column(modifier = Modifier.padding(top = Spacing.md)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = Sms2WalletTheme.extendedColors.warningContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = SolarIcons.WarningAmber,
                            contentDescription = null,
                            tint = Sms2WalletTheme.extendedColors.onWarningContainer,
                            modifier = Modifier.size(IconSize.md)
                        )
                        Text(
                            text = if (attentionCount == 1) {
                                "1 transaction needs a closer look before pushing"
                            } else {
                                "$attentionCount transactions need a closer look before pushing"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sms2WalletTheme.extendedColors.onWarningContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilterChip(
                        selected = !filterAttentionOnly,
                        onClick = { if (filterAttentionOnly) onToggleFilterAttention() },
                        label = { Text("All ($total)") }
                    )
                    FilterChip(
                        selected = filterAttentionOnly,
                        onClick = { if (!filterAttentionOnly) onToggleFilterAttention() },
                        label = { Text("Needs attention ($attentionCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Sms2WalletTheme.extendedColors.warningContainer,
                            selectedLabelColor = Sms2WalletTheme.extendedColors.onWarningContainer
                        )
                    )
                }
            }
        }
    }
}

@Preview(name = "Review queue - Light", showBackground = true)
@Composable
private fun ReviewQueueLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        ReviewQueueContent(
            state = SampleData.reviewQueue,
            onOpenTransaction = {},
            onPush = {},
            onDismiss = {},
            onToggleMultiSelect = {},
            onToggleSelected = { _, _ -> },
            onBulkPush = {},
            onBulkDismiss = {},
            onDismissAll = {}
        )
    }
}

@Preview(name = "Review queue - Dark", showBackground = true)
@Composable
private fun ReviewQueueDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) {
        ReviewQueueContent(
            state = SampleData.reviewQueue,
            onOpenTransaction = {},
            onPush = {},
            onDismiss = {},
            onToggleMultiSelect = {},
            onToggleSelected = { _, _ -> },
            onBulkPush = {},
            onBulkDismiss = {},
            onDismissAll = {}
        )
    }
}

@Preview(name = "Review queue - Empty", showBackground = true)
@Composable
private fun ReviewQueueEmptyPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        ReviewQueueContent(
            state = SampleData.emptyReviewQueue,
            onOpenTransaction = {},
            onPush = {},
            onDismiss = {},
            onToggleMultiSelect = {},
            onToggleSelected = { _, _ -> },
            onBulkPush = {},
            onBulkDismiss = {},
            onDismissAll = {}
        )
    }
}
