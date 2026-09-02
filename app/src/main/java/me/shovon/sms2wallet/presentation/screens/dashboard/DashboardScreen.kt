package me.shovon.sms2wallet.presentation.screens.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Surface
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.SectionDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MinTouchTarget
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.model.DashboardUiState
import me.shovon.sms2wallet.presentation.model.RateLimitUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.TokenHealth
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/**
 * Home tab: today/this-week push counters, pending review count, last sync time, token
 * health, and the Wallet API rate-limit budget. The FAB opens the add-cash-expense sheet.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAddCashExpense: () -> Unit,
    onViewReviewQueue: () -> Unit
) {
    Sms2WalletScaffold(
        title = "Dashboard",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCashExpense,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add cash expense") }
            )
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
                // Clear of the FAB, which floats over the list.
                bottom = Spacing.xxl * 2
            )
        ) {
            item(key = "counters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Pushed today",
                        value = state.pushedToday.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Pushed this week",
                        value = state.pushedThisWeek.toString()
                    )
                }
            }

            item(key = "pending") {
                PendingReviewCard(
                    pendingCount = state.pendingReviewCount,
                    onClick = onViewReviewQueue,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }

            item(key = "status-header") {
                SectionHeader(
                    title = "Status",
                    modifier = Modifier.padding(top = Spacing.xl)
                )
            }
            // One container for the three status readings rather than three separate cards:
            // they answer the same question ("is the pipeline healthy?") and belong together.
            item(key = "status-body") {
                GroupedContainer {
                    InfoRow(
                        icon = Icons.Filled.Sync,
                        title = "Last sync",
                        value = state.lastSyncLabel ?: "Never synced yet"
                    )
                    SectionDivider(startInset = STATUS_DIVIDER_INSET)
                    TokenHealthRow(tokenHealth = state.tokenHealth)
                    SectionDivider(startInset = STATUS_DIVIDER_INSET)
                    RateLimitRow(rateLimit = state.rateLimit)
                }
            }
        }
    }
}

/** Divider inset past the status icons, so the rule starts at the text column. */
private val STATUS_DIVIDER_INSET = 56.dp

/** One of the two headline counters. A grouped surface, not a Card - no shadow, no competing edge. */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = groupedSurfaceColor()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

/**
 * The one call to action on this screen, so it keeps the filled primary-container treatment
 * that separates it from the neutral status readings below.
 */
@Composable
private fun PendingReviewCard(pendingCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pending review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when (pendingCount) {
                        0 -> "All caught up"
                        1 -> "1 transaction waiting"
                        else -> "$pendingCount transactions waiting"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            }
            Text(
                text = pendingCount.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TokenHealthRow(tokenHealth: TokenHealth) {
    val (icon: ImageVector, label: String) = when (tokenHealth) {
        TokenHealth.VALID -> Icons.Filled.CheckCircle to "Wallet token is valid"
        TokenHealth.EXPIRING_SOON -> Icons.Filled.HourglassTop to "Wallet token expires soon"
        TokenHealth.SYNCING -> Icons.Filled.Sync to "Wallet is still syncing"
        TokenHealth.INVALID -> Icons.Filled.Error to "Wallet token is invalid"
        TokenHealth.UNKNOWN -> Icons.Filled.Error to "Token status unknown"
    }
    InfoRow(icon = icon, title = "Token health", value = label)
}

/** Label-over-value status row inside the grouped Status container. */
@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.lg)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
        }
    }
}

/**
 * API budget row. Carries a leading icon like the two rows above it so all three text columns
 * start on the same x - without it the meter's label hung off the container edge while the
 * others were inset past their icons.
 */
@Composable
private fun RateLimitRow(rateLimit: RateLimitUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = Icons.Filled.Speed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "API budget",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${rateLimit.used} / ${rateLimit.limit} ${rateLimit.windowLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LinearProgressIndicator(
                progress = { rateLimit.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .height(PROGRESS_HEIGHT),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

private val PROGRESS_HEIGHT = 8.dp

@Preview(name = "Dashboard - Light", showBackground = true)
@Composable
private fun DashboardScreenLightPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
        DashboardScreen(state = SampleData.dashboard, onAddCashExpense = {}, onViewReviewQueue = {})
    }
}

@Preview(name = "Dashboard - Dark", showBackground = true)
@Composable
private fun DashboardScreenDarkPreview() {
    Sms2WalletTheme(darkTheme = true, useDynamicColor = false) {
        DashboardScreen(state = SampleData.dashboard, onAddCashExpense = {}, onViewReviewQueue = {})
    }
}
