package me.shovon.sms2wallet.presentation.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import me.shovon.sms2wallet.presentation.components.EmptyState
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsScreenUiState
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsUiState
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons
import me.shovon.sms2wallet.domain.model.ThemeMode

/**
 * "Unmatched SMS" sub-screen, reached from Activity: raw SMS messages no parser could match, so
 * the user can see what's being missed (or confirm it's all noise like OTPs/bill reminders).
 */
@Composable
fun UnmatchedSmsContent(state: UnmatchedSmsScreenUiState, onBack: () -> Unit) {
    Sms2WalletScaffold(
        title = "Unmatched SMS",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(PhosphorIcons.ArrowBack, contentDescription = "Back")
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

        if (state.items.isEmpty()) {
            EmptyState(
                icon = PhosphorIcons.MarkEmailRead,
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
                bottom = Spacing.xxl
            )
        ) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                if (index > 0) GroupedRowDivider()
                UnmatchedSmsRow(item = item, index = index, count = state.items.size)
            }
        }
    }
}

@Composable
private fun UnmatchedSmsRow(item: UnmatchedSmsUiState, index: Int, count: Int) {
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
                Text(
                    text = item.sender,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = item.receivedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.bodyPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

@Preview(name = "Unmatched SMS - Light", showBackground = true)
@Composable
private fun UnmatchedSmsLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, useDynamicColor = false) {
        UnmatchedSmsContent(state = SampleData.unmatchedSms, onBack = {})
    }
}

@Preview(name = "Unmatched SMS - Dark", showBackground = true)
@Composable
private fun UnmatchedSmsDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, useDynamicColor = false) {
        UnmatchedSmsContent(state = SampleData.unmatchedSms, onBack = {})
    }
}

@Preview(name = "Unmatched SMS - Empty", showBackground = true)
@Composable
private fun UnmatchedSmsEmptyPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, useDynamicColor = false) {
        UnmatchedSmsContent(state = SampleData.emptyUnmatchedSms, onBack = {})
    }
}
