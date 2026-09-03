package me.shovon.sms2wallet.presentation.screens.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.model.ParserMatchResultUiState
import me.shovon.sms2wallet.presentation.model.ParserPlaygroundUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons
import me.shovon.sms2wallet.domain.model.ThemeMode

/**
 * Parser playground, reached from Settings: paste an SMS sender + body and see which registered
 * parsers match it and every field each one extracts. Runs the real `:bd-sms-parsers` parsers.
 */
@Composable
fun ParserPlaygroundScreen(
    onBack: () -> Unit,
    viewModel: ParserPlaygroundViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ParserPlaygroundContent(
        state = state,
        onBack = onBack,
        onSenderChange = viewModel::onSenderChange,
        onBodyChange = viewModel::onBodyChange,
        onRun = viewModel::run
    )
}

@Composable
fun ParserPlaygroundContent(
    state: ParserPlaygroundUiState,
    onBack: () -> Unit,
    onSenderChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onRun: () -> Unit
) {
    Sms2WalletScaffold(
        title = "Parser playground",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(PhosphorIcons.ArrowBack, contentDescription = "Back")
            }
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
                bottom = Spacing.xxl
            )
        ) {
            item(key = "sender") {
                AppTextField(
                    label = "Sender",
                    value = state.senderInput,
                    onValueChange = onSenderChange,
                    placeholder = "e.g. bKash"
                )
            }
            item(key = "body") {
                AppTextField(
                    label = "SMS body",
                    value = state.bodyInput,
                    onValueChange = onBodyChange,
                    placeholder = "Paste the full message",
                    singleLine = false
                )
            }
            item(key = "run") {
                Button(
                    onClick = onRun,
                    enabled = state.bodyInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                ) {
                    Text("Run parsers")
                }
            }
            if (state.hasRun) {
                item(key = "results-header") {
                    SectionHeader(
                        title = "Results",
                        supportingText = "${state.results.count { it.matched }} of ${state.results.size} parsers matched",
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                }
                itemsIndexed(state.results, key = { _, r -> r.providerName }) { index, result ->
                    if (index > 0) GroupedRowDivider()
                    ParserResultCard(result = result, index = index, count = state.results.size)
                }
            }
        }
    }
}

@Composable
private fun ParserResultCard(result: ParserMatchResultUiState, index: Int, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedRowShape(index = index, count = count),
        // A match is tinted so the eye can find it without reading every provider name.
        color = if (result.matched) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            groupedSurfaceColor()
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            ParserResultHeader(result)
            if (result.matched) {
                result.extractedFields.forEach { field ->
                    Text(
                        text = "${field.label}: ${field.value}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (result.failureReason != null) {
                Text(
                    text = result.failureReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParserResultHeader(result: ParserMatchResultUiState) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = result.providerName, style = MaterialTheme.typography.titleMedium)
        Icon(
            imageVector = if (result.matched) PhosphorIcons.CheckCircle else PhosphorIcons.Cancel,
            contentDescription = if (result.matched) "Matched" else "No match",
            tint = if (result.matched) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Parser playground - Light", showBackground = true)
@Composable
private fun ParserPlaygroundLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, useDynamicColor = false) {
        ParserPlaygroundContent(
            state = SampleData.parserPlayground,
            onBack = {},
            onSenderChange = {},
            onBodyChange = {},
            onRun = {}
        )
    }
}

@Preview(name = "Parser playground - Dark", showBackground = true)
@Composable
private fun ParserPlaygroundDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, useDynamicColor = false) {
        ParserPlaygroundContent(
            state = SampleData.parserPlayground,
            onBack = {},
            onSenderChange = {},
            onBodyChange = {},
            onRun = {}
        )
    }
}
