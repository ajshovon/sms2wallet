package me.shovon.sms2wallet.presentation.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.MoneyText
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.ActivityUiState
import me.shovon.sms2wallet.presentation.model.PushLogEntryUiState
import me.shovon.sms2wallet.presentation.model.PushLogStatus
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing

private enum class ActivityFilter {
    ALL, SUCCESS, FAILED
}

/**
 * Activity tab: the push log (what was sent to Wallet, when, and whether it succeeded), with a
 * retry affordance for failures, status filtering, and a link to the "Unmatched SMS" sub-screen.
 */
@Composable
fun ActivityContent(
    state: ActivityUiState,
    onOpenUnmatchedSms: () -> Unit,
    onRetry: (Long) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    val filteredLogs = remember(state.logs, selectedFilter) {
        when (selectedFilter) {
            ActivityFilter.ALL -> state.logs
            ActivityFilter.SUCCESS -> state.logs.filter { it.status == PushLogStatus.SUCCESS }
            ActivityFilter.FAILED -> state.logs.filter { it.status == PushLogStatus.FAILED }
        }
    }

    val successCount = remember(state.logs) { state.logs.count { it.status == PushLogStatus.SUCCESS } }
    val failedCount = remember(state.logs) { state.logs.count { it.status == PushLogStatus.FAILED } }

    Sms2WalletScaffold(
        title = "Activity",
        actions = {
            TextButton(onClick = onOpenUnmatchedSms) {
                Icon(
                    imageVector = SolarIcons.MarkEmailUnread,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(Modifier.size(Spacing.xs))
                Text("Unmatched SMS")
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

        if (state.logs.isEmpty()) {
            EmptyState(
                icon = SolarIcons.History,
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
                bottom = Spacing.lg
            )
        ) {
            // Overview metrics bar
            item(key = "metrics-summary") {
                val rate = if (state.logs.isNotEmpty()) {
                    ((successCount.toFloat() / state.logs.size) * 100).toInt()
                } else 100

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.md),
                    shape = MaterialTheme.shapes.large,
                    color = groupedSurfaceColor()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActivityMetricItem(
                            label = "Success Rate",
                            value = "$rate%",
                            color = if (rate >= 90) Sms2WalletTheme.extendedColors.income else MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        ActivityMetricItem(
                            label = "Pushed",
                            value = successCount.toString(),
                            color = Sms2WalletTheme.extendedColors.income
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        ActivityMetricItem(
                            label = "Failed",
                            value = failedCount.toString(),
                            color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status filter chips
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilterChip(
                        selected = selectedFilter == ActivityFilter.ALL,
                        onClick = { selectedFilter = ActivityFilter.ALL },
                        label = { Text("All (${state.logs.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == ActivityFilter.SUCCESS,
                        onClick = { selectedFilter = ActivityFilter.SUCCESS },
                        label = { Text("Pushed ($successCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Sms2WalletTheme.extendedColors.income.copy(alpha = 0.2f),
                            selectedLabelColor = Sms2WalletTheme.extendedColors.income
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == ActivityFilter.FAILED,
                        onClick = { selectedFilter = ActivityFilter.FAILED },
                        label = { Text("Failed ($failedCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                item(key = "empty-filter") {
                    EmptyState(
                        icon = SolarIcons.CheckCircle,
                        title = "No ${selectedFilter.name.lowercase()} logs",
                        description = "Everything matches your clean filter.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl)
                    )
                }
            } else {
                itemsIndexed(filteredLogs, key = { _, entry -> entry.id }) { index, entry ->
                    if (index > 0) GroupedRowDivider()
                    PushLogRow(
                        entry = entry,
                        index = index,
                        count = filteredLogs.size,
                        onRetry = { entry.transactionId?.let(onRetry) }
                    )
                }
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
    val (icon, statusColor, statusContainer, statusLabel) = when (entry.status) {
        PushLogStatus.SUCCESS -> Quadruple(
            SolarIcons.CheckCircle,
            extended.income,
            extended.income.copy(alpha = 0.15f),
            "Pushed"
        )
        PushLogStatus.FAILED -> Quadruple(
            SolarIcons.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            "Failed"
        )
        PushLogStatus.PENDING -> Quadruple(
            SolarIcons.HourglassTop,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
            "Pending"
        )
        PushLogStatus.RETRYING -> Quadruple(
            SolarIcons.Sync,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
            "Retrying"
        )
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
                // Status icon avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = statusLabel,
                        tint = statusColor,
                        modifier = Modifier.size(IconSize.md)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$statusLabel • ${entry.timeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        modifier = Modifier.padding(top = Spacing.xxs)
                    )
                }

                MoneyText(
                    amount = entry.amount,
                    direction = entry.direction,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Error callout box for failed pushes
            entry.errorMessage?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Spacing.sm)
                    )
                }
            }

            if (entry.isRetryable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = SolarIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.sm)
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text("Retry push")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMetricItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        }
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Preview(name = "Activity - Light", showBackground = true)
@Composable
private fun ActivityScreenLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        ActivityContent(state = SampleData.activity, onOpenUnmatchedSms = {}, onRetry = {})
    }
}

@Preview(name = "Activity - Dark", showBackground = true)
@Composable
private fun ActivityScreenDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) {
        ActivityContent(state = SampleData.activity, onOpenUnmatchedSms = {}, onRetry = {})
    }
}

@Preview(name = "Activity - Empty", showBackground = true)
@Composable
private fun ActivityScreenEmptyPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        ActivityContent(state = SampleData.emptyActivity, onOpenUnmatchedSms = {}, onRetry = {})
    }
}
