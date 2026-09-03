package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ConnectionStatus
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.SettingsUiState
import me.shovon.sms2wallet.presentation.model.WalletCatalogueUiState
import me.shovon.sms2wallet.presentation.model.WalletConnectionUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MotionDuration
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.StandardEasing

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
    onSyncWalletData: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
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
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xxl
            )
        ) {
            item(key = "appearance-header") {
                SectionHeader(title = "Appearance")
            }
            item(key = "appearance-body") {
                ThemeModeSelector(
                    selected = state.themeMode,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.padding(bottom = Spacing.xl)
                )
            }

            item(key = "wallet-header") {
                SectionHeader(
                    title = "Wallet connection",
                    supportingText = "Your API token is stored encrypted on this device."
                )
            }
            item(key = "wallet-body") {
                WalletConnectionSection(
                    state = state.walletConnection,
                    onTokenChange = onTokenChange,
                    onToggleVisibility = onToggleTokenVisibility,
                    onTestConnection = onTestConnection
                )
            }

            item(key = "catalogue") {
                WalletCatalogueRow(
                    state = state.catalogue,
                    enabled = state.walletConnection.hasStoredToken,
                    onSync = onSyncWalletData,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }

            item(key = "parsers-header") {
                SectionHeader(
                    title = "Parsers",
                    supportingText = "Which providers are read, and which auto-push.",
                    modifier = Modifier.padding(top = Spacing.xl),
                    trailing = {
                        TextButton(onClick = onOpenParserPlayground) {
                            Icon(
                                imageVector = PhosphorIcons.Science,
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.md)
                            )
                            Spacer(Modifier.size(Spacing.xs))
                            Text("Playground")
                        }
                    }
                )
            }
            itemsIndexed(
                items = state.parserSettings,
                key = { _, parser -> parser.providerName }
            ) { index, parserSetting ->
                ParserSettingRow(
                    setting = parserSetting,
                    index = index,
                    count = state.parserSettings.size,
                    onEnabledChange = { onParserEnabledChange(parserSetting.providerName, it) },
                    onAutoPushChange = { onParserAutoPushChange(parserSetting.providerName, it) }
                )
                if (index < state.parserSettings.lastIndex) GroupedRowDivider()
            }

            item(key = "mapping-header") {
                SectionHeader(
                    title = "Account mapping",
                    supportingText = "Where transactions from each SMS source are filed in Wallet.",
                    modifier = Modifier.padding(top = Spacing.xl)
                )
            }
            if (state.accountMappings.isEmpty()) {
                item(key = "mapping-empty") { NoSourcesYet() }
            } else {
                itemsIndexed(
                    items = state.accountMappings,
                    key = { _, mapping -> mapping.sourceId }
                ) { index, mapping ->
                    AccountMappingRow(
                        mapping = mapping,
                        index = index,
                        count = state.accountMappings.size,
                        onAccountSelected = { account -> onAccountMappingChange(mapping.sourceId, account) }
                    )
                    if (index < state.accountMappings.lastIndex) GroupedRowDivider()
                }
            }

            item(key = "reminders-header") {
                SectionHeader(
                    title = "Reminders",
                    supportingText = "A nudge to log cash spending that never produced an SMS.",
                    modifier = Modifier.padding(top = Spacing.xl)
                )
            }
            item(key = "reminders-body") {
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

/**
 * Theme picker.
 *
 * A single-choice segmented row rather than a dialog or a list of radio rows: there are only four
 * options, the labels are short, and - unusually for a setting - the result is visible instantly on
 * the screen you are already looking at. Hiding that behind a dialog would put a scrim over the
 * very thing the user is trying to judge.
 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = mode.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

/** Short, user-facing name for each mode. "AMOLED" is kept as-is: it is what users search for. */
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
}

/**
 * What the app currently knows about your Wallet, and how to bring it up to date.
 *
 * Accounts and categories are cached locally, so anything created in Wallet after the last sync
 * simply will not appear in the pickers. Rather than leave that as a mystery, this states what
 * is cached, when it was fetched, and offers the fix in the same place.
 */
@Composable
private fun WalletCatalogueRow(
    state: WalletCatalogueUiState,
    enabled: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    GroupedContainer(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Accounts and categories",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when {
                        state.lastSyncedLabel == null -> "Not synced yet"
                        else -> "${state.accountCount} accounts · ${state.categoryCount} categories · " +
                            "updated ${state.lastSyncedLabel}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }

            Crossfade(
                targetState = state.isSyncing,
                animationSpec = tween(MotionDuration.QUICK_MILLIS, easing = StandardEasing),
                label = "sync-state"
            ) { syncing ->
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.lg),
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(onClick = onSync, enabled = enabled) {
                    Icon(
                        imageVector = PhosphorIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.md)
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Sync")
                }
            }
            }
        }
    }
}

/** Shown when no SMS source has been seen yet, so the mapping list would otherwise be blank. */
@Composable
private fun NoSourcesYet() {
    GroupedContainer {
        Text(
            text = "No sources detected yet. Once a transaction SMS arrives, its provider appears here to be mapped.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg)
        )
    }
}

@Composable
private fun WalletConnectionSection(
    state: WalletConnectionUiState,
    onTokenChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onTestConnection: () -> Unit
) {
    GroupedContainer {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            AppTextField(
                label = "API token",
                value = state.tokenInput,
                onValueChange = onTokenChange,
                placeholder = if (state.hasStoredToken) {
                    "Enter a new token to replace the saved one"
                } else {
                    "Paste your Wallet API token"
                },
                supportingText = if (state.hasStoredToken && state.tokenInput.isEmpty()) {
                    "A token is saved on this device."
                } else {
                    null
                },
                visualTransformation = if (state.isTokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (state.isTokenVisible) {
                                PhosphorIcons.VisibilityOff
                            } else {
                                PhosphorIcons.Visibility
                            },
                            contentDescription = if (state.isTokenVisible) "Hide token" else "Show token"
                        )
                    }
                }
            )

            ConnectionStatusRow(status = state.status)

            OutlinedButton(
                onClick = onTestConnection,
                // Testing the already-saved token is valid with the field left empty.
                enabled = !state.isTesting && (state.tokenInput.isNotBlank() || state.hasStoredToken),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(IconSize.sm),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(Spacing.sm))
                }
                Text(if (state.isTesting) "Testing…" else "Test connection")
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus) {
    val extended = Sms2WalletTheme.extendedColors
    val (icon, label, color) = when (status) {
        is ConnectionStatus.NotTested -> Triple(PhosphorIcons.Error, "Not tested yet", MaterialTheme.colorScheme.onSurfaceVariant)
        is ConnectionStatus.Success -> Triple(PhosphorIcons.CheckCircle, "Connected", extended.income)
        is ConnectionStatus.Syncing -> Triple(
            PhosphorIcons.Sync,
            "Wallet is still syncing - retry in ${status.retryInMinutes} minute${if (status.retryInMinutes == 1) "" else "s"}",
            MaterialTheme.colorScheme.tertiary
        )
        is ConnectionStatus.Failed -> Triple(PhosphorIcons.Error, status.message, MaterialTheme.colorScheme.error)
    }
    Row(
        modifier = Modifier.padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(IconSize.md))
        Text(text = label, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Settings - Light", showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, useDynamicColor = false) {
        SettingsContent(
            state = SampleData.settings,
            onOpenParserPlayground = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onTestConnection = {},
            onSyncWalletData = {},
            onThemeModeChange = {},
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
    Sms2WalletTheme(themeMode = ThemeMode.DARK, useDynamicColor = false) {
        SettingsContent(
            state = SampleData.settings.copy(walletConnection = SampleData.walletConnectionSyncing),
            onOpenParserPlayground = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onTestConnection = {},
            onSyncWalletData = {},
            onThemeModeChange = {},
            onParserEnabledChange = { _, _ -> },
            onParserAutoPushChange = { _, _ -> },
            onAccountMappingChange = { _, _ -> },
            onReminderEnabledChange = {},
            onReminderTimeChange = { _, _ -> },
            onReminderSkipCountChange = {}
        )
    }
}
