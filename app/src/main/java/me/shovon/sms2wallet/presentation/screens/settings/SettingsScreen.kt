package me.shovon.sms2wallet.presentation.screens.settings

import me.shovon.sms2wallet.presentation.model.LearnedCategoryUiState
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.ProviderAvatar
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.ConnectionStatus
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.SettingsUiState
import me.shovon.sms2wallet.presentation.model.WalletCatalogueUiState
import me.shovon.sms2wallet.presentation.model.WalletConnectionUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MinTouchTarget
import me.shovon.sms2wallet.presentation.theme.MotionDuration
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.StandardEasing
import me.shovon.sms2wallet.presentation.theme.swatch

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
    onAccentColorChange: (AccentColor) -> Unit,
    onGeminiKeyChange: (String) -> Unit,
    onToggleGeminiKeyVisibility: () -> Unit,
    onTestGeminiKey: () -> Unit,
    onClearGeminiKey: () -> Unit,
    onGeminiModelChange: (String) -> Unit,
    onShareCategoryNamesChange: (Boolean) -> Unit,
    onShareAccountNamesChange: (Boolean) -> Unit,
    onShareMerchantNamesChange: (Boolean) -> Unit,
    onDeleteLearnedCategory: (Long) -> Unit,
    onDefaultAccountChange: (String) -> Unit,
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
                bottom = Spacing.lg
            )
        ) {
            item(key = "appearance-header") {
                SectionHeader(title = "Appearance")
            }
            item(key = "appearance-theme") {
                ThemeModeSelector(selected = state.themeMode, onSelect = onThemeModeChange)
            }
            item(key = "appearance-accent") {
                AccentColorPicker(
                    selected = state.accentColor,
                    onSelect = onAccentColorChange,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.md)
                )
            }
            item(key = "appearance-preview") {
                ThemePreviewCard(
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

            item(key = "intelligence-header") {
                SectionHeader(
                    title = "Intelligence",
                    supportingText = "Add a transaction by typing it, e.g. \"uber 120\".",
                    modifier = Modifier.padding(top = Spacing.xl)
                )
            }
            item(key = "intelligence-body") {
                IntelligenceSection(
                    state = state.intelligence,
                    onKeyChange = onGeminiKeyChange,
                    onToggleKeyVisibility = onToggleGeminiKeyVisibility,
                    onTestKey = onTestGeminiKey,
                    onClearKey = onClearGeminiKey,
                    onModelChange = onGeminiModelChange,
                    onShareCategoryNamesChange = onShareCategoryNamesChange,
                    onShareAccountNamesChange = onShareAccountNamesChange,
                    onShareMerchantNamesChange = onShareMerchantNamesChange,
                    onDefaultAccountChange = onDefaultAccountChange
                )
            }

            if (state.learnedCategories.isNotEmpty()) {
                item(key = "learned-header") {
                    SectionHeader(
                        title = "Learned categories",
                        supportingText = "Remembered when you push a transaction. Used before " +
                            "anything is sent to Gemini.",
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                }
                itemsIndexed(
                    items = state.learnedCategories,
                    key = { _, learned -> learned.id }
                ) { index, learned ->
                    LearnedCategoryRow(
                        learned = learned,
                        index = index,
                        count = state.learnedCategories.size,
                        onDelete = { onDeleteLearnedCategory(learned.id) }
                    )
                    if (index < state.learnedCategories.lastIndex) GroupedRowDivider()
                }
            }

            item(key = "parsers-header") {
                SectionHeader(
                    title = "Parsers",
                    supportingText = "Which providers are read, and which auto-push.",
                    modifier = Modifier.padding(top = Spacing.xl),
                    trailing = {
                        TextButton(onClick = onOpenParserPlayground) {
                            Icon(
                                imageVector = SolarIcons.Science,
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
            val icon = when (mode) {
                ThemeMode.SYSTEM -> SolarIcons.DeviceMobile
                ThemeMode.LIGHT -> SolarIcons.Sun
                ThemeMode.DARK -> SolarIcons.Moon
                ThemeMode.AMOLED -> SolarIcons.Sparkle
            }
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.sm)
                        )
                        Text(
                            text = mode.label(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            )
        }
    }
}

/**
 * Accent picker: a row of swatches showing the actual seed each palette is generated from.
 *
 * Swatches rather than a list of colour names, because the choice is entirely visual - a name
 * like "Rose" tells you far less than the colour itself, and the whole row fits on one line so
 * every option is comparable at a glance without opening anything.
 *
 * Selection is a ring drawn *outside* the swatch rather than a check mark on top of it: a mark
 * placed over the colour would have to be light or dark and so would fail against half the
 * options, and growing the swatch itself would make the row reflow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorPicker(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val options = AccentColor.entries.filter { it != AccentColor.DYNAMIC || dynamicSupported }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Accent",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            options.forEach { accent ->
                AccentSwatch(
                    accent = accent,
                    isSelected = accent == selected,
                    onClick = { onSelect(accent) }
                )
            }
        }
        // Names the current accent in plain text. A 2dp ring is a thin thing to hang the answer
        // to "which one is on?" on - especially against seven similar circles - and stating it
        // means the selection is legible to everyone, and is read aloud by any screen reader that
        // reaches this line, rather than depending on the swatch's own semantics being surfaced.
        Text(
            text = if (selected == AccentColor.DYNAMIC) {
                "Wallpaper colours - following your system palette."
            } else {
                "${selected.spokenLabel().removeSuffix(" accent")} - the rest of the palette is generated from it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
}

@Composable
private fun AccentSwatch(accent: AccentColor, isSelected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            // The visual dot is 32dp but the tappable box is a full 48dp, so the row stays
            // compact without shrinking the touch target below the Android minimum.
            .size(MinTouchTarget)
            .clip(CircleShape)
            .selectable(selected = isSelected, onClick = onClick)
            // One merged, focusable node per swatch carrying label, role and state. The role and
            // `selected` are set here rather than relying on `selectable` alone because that was
            // not surfacing them to the accessibility tree, leaving every swatch announced
            // identically with no indication of which accent was active. stateDescription is the
            // part a screen reader is guaranteed to speak.
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = accent.spokenLabel()
                stateDescription = if (isSelected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .clip(CircleShape)
                .background(accent.swatch())
                .then(
                    if (isSelected) {
                        Modifier.border(width = 2.dp, color = ring, shape = CircleShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            // Material You cannot be shown as a single swatch honestly - it is whatever the
            // wallpaper yields - so it is marked rather than merely coloured.
            if (accent == AccentColor.DYNAMIC) {
                Icon(
                    imageVector = SolarIcons.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(IconSize.sm)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

private val SWATCH_SIZE = 32.dp

/** Spoken label for a swatch, since a colour alone conveys nothing to a screen reader. */
private fun AccentColor.spokenLabel(): String = when (this) {
    AccentColor.DYNAMIC -> "Wallpaper colours"
    AccentColor.BRAND -> "Teal accent"
    AccentColor.BLUE -> "Blue accent"
    AccentColor.VIOLET -> "Violet accent"
    AccentColor.ROSE -> "Rose accent"
    AccentColor.AMBER -> "Amber accent"
    AccentColor.FOREST -> "Forest accent"
}

/** Short, user-facing name for each mode. "AMOLED" is kept as-is: it is what users search for. */
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
}

/**
 * Interactive preview of the selected theme palette and components.
 */
@Composable
private fun ThemePreviewCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = groupedSurfaceColor()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Theme Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Active Palette",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                    )
                }
            }

            // Mini Transaction Preview
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    ProviderAvatar(providerName = "bKash", size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SHWAPNO SUPERSTORE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            StatusBadge(text = "Groceries", intent = BadgeIntent.NEUTRAL)
                            StatusBadge(text = "bKash Personal", intent = BadgeIntent.INFO)
                        }
                    }
                    Text(
                        text = "-৳1,250.00",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Sms2WalletTheme.extendedColors.expense
                    )
                }
            }

            // Mini Component Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tonal Action")
                }
                StatusBadge(text = "Verified", intent = BadgeIntent.INFO)
                StatusBadge(text = "Review", intent = BadgeIntent.WARNING)
            }
        }
    }
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
                TextButton(
                    onClick = onSync,
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = "Sync accounts and categories from Wallet"
                    }
                ) {
                    Icon(
                        imageVector = SolarIcons.Refresh,
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
                                SolarIcons.VisibilityOff
                            } else {
                                SolarIcons.Visibility
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
        is ConnectionStatus.NotTested -> Triple(SolarIcons.Error, "Not tested yet", MaterialTheme.colorScheme.onSurfaceVariant)
        is ConnectionStatus.Success -> Triple(SolarIcons.CheckCircle, "Connected", extended.income)
        is ConnectionStatus.Syncing -> Triple(
            SolarIcons.Sync,
            "Wallet is still syncing - retry in ${status.retryInMinutes} minute${if (status.retryInMinutes == 1) "" else "s"}",
            MaterialTheme.colorScheme.tertiary
        )
        is ConnectionStatus.Failed -> Triple(SolarIcons.Error, status.message, MaterialTheme.colorScheme.error)
    }
    Row(
        // Announced when it changes: the connection verdict lands after the network call, with
        // nothing else moving focus to it.
        modifier = Modifier
            .padding(top = Spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
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
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        SettingsContent(
            state = SampleData.settings,
            onOpenParserPlayground = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onTestConnection = {},
            onSyncWalletData = {},
            onThemeModeChange = {},
            onAccentColorChange = {},
            onGeminiKeyChange = {},
            onToggleGeminiKeyVisibility = {},
            onTestGeminiKey = {},
            onClearGeminiKey = {},
            onGeminiModelChange = {},
            onShareCategoryNamesChange = {},
            onShareAccountNamesChange = {},
            onShareMerchantNamesChange = {},
            onDeleteLearnedCategory = {},
            onDefaultAccountChange = {},
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
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) {
        SettingsContent(
            state = SampleData.settings.copy(walletConnection = SampleData.walletConnectionSyncing),
            onOpenParserPlayground = {},
            onTokenChange = {},
            onToggleTokenVisibility = {},
            onTestConnection = {},
            onSyncWalletData = {},
            onThemeModeChange = {},
            onAccentColorChange = {},
            onGeminiKeyChange = {},
            onToggleGeminiKeyVisibility = {},
            onTestGeminiKey = {},
            onClearGeminiKey = {},
            onGeminiModelChange = {},
            onShareCategoryNamesChange = {},
            onShareAccountNamesChange = {},
            onShareMerchantNamesChange = {},
            onDeleteLearnedCategory = {},
            onDefaultAccountChange = {},
            onParserEnabledChange = { _, _ -> },
            onParserAutoPushChange = { _, _ -> },
            onAccountMappingChange = { _, _ -> },
            onReminderEnabledChange = {},
            onReminderTimeChange = { _, _ -> },
            onReminderSkipCountChange = {}
        )
    }
}

/**
 * One learned merchant->category pairing, with the way to forget it.
 *
 * Delete needs no confirmation: it removes a shortcut, not data. The next transaction from this
 * merchant simply asks again, which is exactly the state the app was in before it learned.
 */
@Composable
private fun LearnedCategoryRow(
    learned: LearnedCategoryUiState,
    index: Int,
    count: Int,
    onDelete: () -> Unit
) {
    Surface(
        shape = groupedRowShape(index, count),
        color = groupedSurfaceColor()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = learned.keyword,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = learned.categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = SolarIcons.Close,
                    contentDescription = "Forget ${learned.keyword}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.md)
                )
            }
        }
    }
}
