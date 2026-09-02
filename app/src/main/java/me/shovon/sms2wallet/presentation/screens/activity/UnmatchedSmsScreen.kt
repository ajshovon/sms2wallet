package me.shovon.sms2wallet.presentation.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Card
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
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsScreenUiState
import me.shovon.sms2wallet.presentation.model.UnmatchedSmsUiState
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.MarkEmailRead,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.items, key = { it.id }) { item ->
                UnmatchedSmsRow(item)
            }
        }
    }
}

@Composable
private fun UnmatchedSmsRow(item: UnmatchedSmsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = item.sender, style = MaterialTheme.typography.titleSmall)
            Text(text = item.bodyPreview, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = item.receivedAtLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Unmatched SMS - Light", showBackground = true)
@Composable
private fun UnmatchedSmsLightPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
        UnmatchedSmsContent(state = SampleData.unmatchedSms, onBack = {})
    }
}

@Preview(name = "Unmatched SMS - Dark", showBackground = true)
@Composable
private fun UnmatchedSmsDarkPreview() {
    Sms2WalletTheme(darkTheme = true, useDynamicColor = false) {
        UnmatchedSmsContent(state = SampleData.unmatchedSms, onBack = {})
    }
}

@Preview(name = "Unmatched SMS - Empty", showBackground = true)
@Composable
private fun UnmatchedSmsEmptyPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
        UnmatchedSmsContent(state = SampleData.emptyUnmatchedSms, onBack = {})
    }
}
