package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ConnectionStatus
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.SettingsUiState
import me.shovon.sms2wallet.presentation.model.WalletConnectionUiState
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/**
 * Settings tab: wallet connection, per-provider parser toggles, account mapping, reminders.
 *
 * Stateless and hoisted - state comes from [me.shovon.sms2wallet.presentation.screens.settings.SettingsViewModel],
 * so toggles persist to DataStore instead of being lost on the next tab switch.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onOpenParserPlayground: () -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onTestConnection: () -> Unit,
    onParserEnabledChange: (String, Boolean) -> Unit,
    onParserAutoPushChange: (String, Boolean) -> Unit,
    onAccountMappingChange: (String, String) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
    onReminderSkipCountChange: (Int) -> Unit
) {
    Sms2WalletScaffold(title = "Settings") { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Wallet connection") }
            item {
                WalletConnectionSection(
                    state = state.walletConnection,
                    onTokenChange = onTokenChange,
                    onToggleVisibility = onToggleTokenVisibility,
                    onTestConnection = onTestConnection
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Parsers")
                    TextButton(onClick = onOpenParserPlayground) {
                        Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Playground")
                    }
                }
            }
            items(state.parserSettings, key = { it.providerName }) { parserSetting ->
                ParserSettingRow(
                    setting = parserSetting,
                    onEnabledChange = { onParserEnabledChange(parserSetting.providerName, it) },
                    onAutoPushChange = { onParserAutoPushChange(parserSetting.providerName, it) }
                )
            }

            item { SectionHeader("Account mapping") }
            items(state.accountMappings, key = { it.sourceId }) { mapping ->
                AccountMappingRow(
                    mapping = mapping,
                    onAccountSelected = { account -> onAccountMappingChange(mapping.sourceId, account) }
                )
            }

            item { SectionHeader("Reminders") }
            item {
                RemindersSection(
                    state = state.reminders,
                    onEnabledChange = onReminderEnabledChange,
                    onTimeChange = onReminderTimeChange,
                    onSkipCountChange = onReminderSkipCountChange
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun WalletConnectionSection(
    state: WalletConnectionUiState,
    onTokenChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onTestConnection: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.tokenInput,
                onValueChange = onTokenChange,
                label = { Text("Wallet API token") },
                singleLine = true,
                visualTransformation = if (state.isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (state.isTokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (state.isTokenVisible) "Hide token" else "Show token"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            ConnectionStatusRow(status = state.status)

            OutlinedButton(
                onClick = onTestConnection,
                enabled = !state.isTesting && state.tokenInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (state.isTesting) "Testing..." else "Test connection")
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus) {
    val extended = Sms2WalletTheme.extendedColors
    val (icon, label, color) = when (status) {
        is ConnectionStatus.NotTested -> Triple(Icons.Filled.Error, "Not tested yet", MaterialTheme.colorScheme.onSurfaceVariant)
        is ConnectionStatus.Success -> Triple(Icons.Filled.CheckCircle, "Connected", extended.income)
        is ConnectionStatus.Syncing -> Triple(
            Icons.Filled.Sync,
            "Wallet is still syncing - retry in ${status.retryInMinutes} minute${if (status.retryInMinutes == 1) "" else "s"}",
            MaterialTheme.colorScheme.tertiary
        )
        is ConnectionStatus.Failed -> Triple(Icons.Filled.Error, status.message, MaterialTheme.colorScheme.error)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Settings - Light", showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
        SettingsContent(
            state = SampleData.settings,
            onOpenParserPlayground = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onTestConnection = {},
            onParserEnabledChange = { _, _ -> },
            onParserAutoPushChange = { _, _ -> },
            onAccountMappingChange = { _, _ -> },
            onReminderEnabledChange = {},
            onReminderTimeChange = { _, _ -> },
            onReminderSkipCountChange = {}
        )
    }
}

@Preview(name = "Settings - Dark", showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    Sms2WalletTheme(darkTheme = true, useDynamicColor = false) {
        SettingsContent(
            state = SampleData.settings.copy(walletConnection = SampleData.walletConnectionSyncing),
            onOpenParserPlayground = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onTestConnection = {},
            onParserEnabledChange = { _, _ -> },
            onParserAutoPushChange = { _, _ -> },
            onAccountMappingChange = { _, _ -> },
            onReminderEnabledChange = {},
            onReminderTimeChange = { _, _ -> },
            onReminderSkipCountChange = {}
        )
    }
}
