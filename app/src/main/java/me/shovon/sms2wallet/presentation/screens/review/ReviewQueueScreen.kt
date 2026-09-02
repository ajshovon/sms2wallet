package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PublishedWithChanges
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ReviewQueueUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing

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
    snackbarHostState: androidx.compose.material3.SnackbarHostState? = null
) {
    var confirmDismissAll by remember { mutableStateOf(false) }

    Sms2WalletScaffold(
        title = if (state.isMultiSelectMode) "${state.selectedIds.size} selected" else "Review",
        snackbarHostState = snackbarHostState,
        navigationIcon = {
            if (state.isMultiSelectMode) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit selection")
                }
            }
        },
        actions = {
            if (state.isMultiSelectMode) {
                IconButton(onClick = onBulkDismiss, enabled = state.selectedIds.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Dismiss selected")
                }
                IconButton(onClick = onBulkPush, enabled = state.selectedIds.isNotEmpty()) {
                    Icon(Icons.Filled.PublishedWithChanges, contentDescription = "Push selected")
                }
            } else if (!state.isEmpty) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.Filled.Checklist, contentDescription = "Select multiple")
                }
                QueueOverflowMenu(onDismissAllClick = { confirmDismissAll = true })
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
                icon = Icons.Filled.Inbox,
                title = "Nothing to review",
                description = "New transactions parsed from your SMS show up here so you can check them before they reach Wallet.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
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
                bottom = Spacing.xxl
            )
            // No blanket vertical arrangement: rows inside a day group must sit flush against
            // each other for the group to read as one container. Spacing is applied per item.
        ) {
            item(key = "summary") {
                QueueSummary(
                    total = state.totalCount,
                    attentionCount = state.attentionCount,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }

            state.groups.forEach { group ->
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
        }
    }

    if (confirmDismissAll) {
        DismissAllDialog(
            count = state.totalCount,
            onConfirm = {
                confirmDismissAll = false
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
private fun QueueOverflowMenu(onDismissAllClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text("Dismiss all", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
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
                imageVector = Icons.Filled.WarningAmber,
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
private fun QueueSummary(total: Int, attentionCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (total == 1) "1 transaction to review" else "$total transactions to review",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Swipe right to push, left to dismiss, or tap to edit first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs)
        )

        if (attentionCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                shape = MaterialTheme.shapes.medium,
                color = Sms2WalletTheme.extendedColors.warningContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
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
                        color = Sms2WalletTheme.extendedColors.onWarningContainer
                    )
                }
            }
        }
    }
}

@Preview(name = "Review queue - Light", showBackground = true)
@Composable
private fun ReviewQueueLightPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
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
    Sms2WalletTheme(darkTheme = true, useDynamicColor = false) {
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
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
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
