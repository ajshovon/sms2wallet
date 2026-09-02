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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            item {
                PendingReviewCard(
                    pendingCount = state.pendingReviewCount,
                    onClick = onViewReviewQueue
                )
            }
            item {
                LastSyncCard(lastSyncLabel = state.lastSyncLabel)
            }
            item {
                TokenHealthCard(tokenHealth = state.tokenHealth)
            }
            item {
                RateLimitCard(rateLimit = state.rateLimit)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PendingReviewCard(pendingCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Pending review",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (pendingCount == 0) "All caught up" else "$pendingCount transaction${if (pendingCount == 1) "" else "s"} waiting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = pendingCount.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LastSyncCard(lastSyncLabel: String?) {
    InfoRowCard(
        icon = Icons.Filled.Sync,
        title = "Last sync",
        value = lastSyncLabel ?: "Never synced yet"
    )
}

@Composable
private fun TokenHealthCard(tokenHealth: TokenHealth) {
    val (icon: ImageVector, label: String) = when (tokenHealth) {
        TokenHealth.VALID -> Icons.Filled.CheckCircle to "Wallet token is valid"
        TokenHealth.EXPIRING_SOON -> Icons.Filled.HourglassTop to "Wallet token expires soon"
        TokenHealth.SYNCING -> Icons.Filled.Sync to "Wallet is still syncing"
        TokenHealth.INVALID -> Icons.Filled.Error to "Wallet token is invalid"
        TokenHealth.UNKNOWN -> Icons.Filled.Error to "Token status unknown"
    }
    InfoRowCard(icon = icon, title = "Token health", value = label)
}

@Composable
private fun InfoRowCard(icon: ImageVector, title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun RateLimitCard(rateLimit: RateLimitUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "API budget", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${rateLimit.used} / ${rateLimit.limit} ${rateLimit.windowLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { rateLimit.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

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
