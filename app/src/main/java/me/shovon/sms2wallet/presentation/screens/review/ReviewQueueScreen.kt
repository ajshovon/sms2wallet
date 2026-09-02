package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PublishedWithChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ReviewQueueUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/**
 * Review queue tab: parsed-but-unpushed transactions grouped by day. Swipe right to push,
 * swipe left to dismiss, tap to edit, or use the select action to bulk-push.
 *
 * Fully stateless and hoisted - all state comes from [me.shovon.sms2wallet.presentation.screens.review.ReviewQueueViewModel],
 * so it survives tab switches and rotation instead of resetting.
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
    onBulkPush: () -> Unit
) {
    Sms2WalletScaffold(
        title = if (state.isMultiSelectMode) "${state.selectedIds.size} selected" else "Review queue (${state.totalCount})",
        navigationIcon = {
            if (state.isMultiSelectMode) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit selection")
                }
            }
        },
        actions = {
            if (state.isMultiSelectMode) {
                IconButton(onClick = onBulkPush, enabled = state.selectedIds.isNotEmpty()) {
                    Icon(Icons.Filled.PublishedWithChanges, contentDescription = "Push selected")
                }
            } else if (!state.isEmpty) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.Filled.Checklist, contentDescription = "Select multiple")
                }
            }
        }
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "Nothing to review",
                description = "New transactions parsed from your SMS will show up here for you to check before they're pushed to Wallet.",
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.dayLabel}") {
                    Text(
                        text = group.dayLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(group.transactions, key = { it.id }) { transaction ->
                    TransactionCard(
                        transaction = transaction,
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
            onBulkPush = {}
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
            onBulkPush = {}
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
            onBulkPush = {}
        )
    }
}
