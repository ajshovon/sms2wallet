package me.shovon.sms2wallet.presentation.screens.activity

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsScreenUiState
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * "Unmatched SMS" sub-screen: raw SMS messages no parser could match, with affordances
 * to test in the Parser Playground, copy text, or dismiss.
 */
@Composable
fun UnmatchedSmsContent(
    state: UnmatchedSmsScreenUiState,
    onBack: () -> Unit,
    onDismiss: (String) -> Unit = {},
    onTestInPlayground: (sender: String, body: String) -> Unit = { _, _ -> }
) {
    Sms2WalletScaffold(
        title = "Unmatched SMS",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(SolarIcons.ArrowBack, contentDescription = "Back")
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

        if (state.items.isEmpty()) {
            EmptyState(
                icon = SolarIcons.MarkEmailRead,
                title = "Nothing unmatched",
                description = "Every relevant SMS has been recognised by a parser. If you expect a transaction to show up, check that its provider is enabled in Settings.",
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
                // These screens carry no bottom bar, so nothing else reserves room for the
                // gesture rail. A fixed inset is a guess; the real one varies by device and by
                // whether the user is on gesture or 3-button navigation.
                bottom = Spacing.xxl +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item(key = "header") {
                SectionHeader(
                    title = "Unmatched messages (${state.items.size})",
                    supportingText = "Messages no enabled parser matched. You can test them in Playground or dismiss them.",
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }

            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                if (index > 0) GroupedRowDivider()
                UnmatchedSmsRow(
                    item = item,
                    index = index,
                    count = state.items.size,
                    onDismiss = { onDismiss(item.id) },
                    onTestInPlayground = { onTestInPlayground(item.sender, item.bodyPreview) }
                )
            }
        }
    }
}

@Composable
private fun UnmatchedSmsRow(
    item: UnmatchedSmsUiState,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
    onTestInPlayground: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedRowShape(index = index, count = count),
        color = groupedSurfaceColor()
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = item.sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = item.receivedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = item.bodyPreview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.sm)
            )

            // Row actions: Test in Playground, Copy, Dismiss
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(item.bodyPreview))
                        Toast.makeText(context, "SMS copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Copy SMS from ${item.sender}"
                    }
                ) {
                    Icon(
                        imageVector = SolarIcons.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Copy")
                }

                TextButton(
                    onClick = onTestInPlayground,
                    modifier = Modifier.semantics {
                        contentDescription = "Test SMS from ${item.sender} in playground"
                    }
                ) {
                    Icon(
                        imageVector = SolarIcons.Science,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Test")
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = "Dismiss SMS from ${item.sender}"
                    }
                ) {
                    Icon(
                        imageVector = SolarIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(name = "Unmatched SMS - Light", showBackground = true)
@Composable
private fun UnmatchedSmsLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        UnmatchedSmsContent(state = SampleData.unmatchedSms, onBack = {})
    }
}

@Preview(name = "Unmatched SMS - Dark", showBackground = true)
@Composable
private fun UnmatchedSmsDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) {
        UnmatchedSmsContent(state = SampleData.unmatchedSms, onBack = {})
    }
}

@Preview(name = "Unmatched SMS - Empty", showBackground = true)
@Composable
private fun UnmatchedSmsEmptyPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        UnmatchedSmsContent(state = SampleData.emptyUnmatchedSms, onBack = {})
    }
}
