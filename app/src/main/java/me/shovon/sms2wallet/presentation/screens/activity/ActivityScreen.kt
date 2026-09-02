package me.shovon.sms2wallet.presentation.screens.activity

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.components.MoneyText
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ActivityUiState
import me.shovon.sms2wallet.presentation.model.PushLogEntryUiState
import me.shovon.sms2wallet.presentation.model.PushLogStatus
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/**
 * Activity tab: the push log (what was sent to Wallet, when, and whether it succeeded), with a
 * retry affordance for failures and a link to the "Unmatched SMS" sub-screen.
 *
 * [onRetry] receives the *transaction* id, not the log row id - a transaction can have several
 * log rows, and it is the transaction that gets requeued.
 */
@Composable
fun ActivityContent(
    state: ActivityUiState,
    onOpenUnmatchedSms: () -> Unit,
    onRetry: (Long) -> Unit
) {
    Sms2WalletScaffold(
        title = "Activity",
        actions = {
            TextButton(onClick = onOpenUnmatchedSms) {
                Icon(
                    imageVector = Icons.Filled.MarkEmailUnread,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(Modifier.size(Spacing.xs))
                Text("Unmatched SMS")
            }
        }
    ) { padding ->
        // Same guard as the review queue: while the first emission is in flight the list is
        // empty but not *known* to be empty, and flashing an empty state reads as "nothing
        // here" rather than "still loading".
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

        if (state.logs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "No push history yet",
                description = "Once transactions are pushed to Wallet, you'll see a log of every attempt here.",
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
        ) {
            itemsIndexed(state.logs, key = { _, entry -> entry.id }) { index, entry ->
                if (index > 0) GroupedRowDivider()
                PushLogRow(
                    entry = entry,
                    index = index,
                    count = state.logs.size,
                    onRetry = { entry.transactionId?.let(onRetry) }
                )
            }
        }
    }
}

@Composable
private fun PushLogRow(
    entry: PushLogEntryUiState,
    index: Int,
    count: Int,
    onRetry: () -> Unit
) {
    val extended = Sms2WalletTheme.extendedColors
    val (icon, statusColor, statusLabel) = when (entry.status) {
        PushLogStatus.SUCCESS -> Triple(Icons.Filled.CheckCircle, extended.income, "Pushed")
        PushLogStatus.FAILED -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed")
        PushLogStatus.PENDING -> Triple(Icons.Filled.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, "Pending")
        PushLogStatus.RETRYING -> Triple(Icons.Filled.Sync, MaterialTheme.colorScheme.tertiary, "Retrying")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedRowShape(index = index, count = count),
        color = groupedSurfaceColor()
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.padding(top = Spacing.xxs)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(IconSize.sm)
                        )
                        Text(
                            text = "$statusLabel • ${entry.timeLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                }
                MoneyText(
                    amount = entry.amount,
                    direction = entry.direction,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            entry.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }

            if (entry.isRetryable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.md)
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Preview(name = "Activity - Light", showBackground = true)
@Composable
private fun ActivityScreenLightPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
        ActivityContent(state = SampleData.activity, onOpenUnmatchedSms = {}, onRetry = {})
    }
}

@Preview(name = "Activity - Dark", showBackground = true)
@Composable
private fun ActivityScreenDarkPreview() {
    Sms2WalletTheme(darkTheme = true, useDynamicColor = false) {
        ActivityContent(state = SampleData.activity, onOpenUnmatchedSms = {}, onRetry = {})
    }
}

@Preview(name = "Activity - Empty", showBackground = true)
@Composable
private fun ActivityScreenEmptyPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
        ActivityContent(state = SampleData.emptyActivity, onOpenUnmatchedSms = {}, onRetry = {})
    }
}
