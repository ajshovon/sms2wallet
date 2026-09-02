package me.shovon.sms2wallet.presentation.screens.playground

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.model.ParserMatchResultUiState
import me.shovon.sms2wallet.presentation.model.ParserPlaygroundUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.senderInput,
                    onValueChange = onSenderChange,
                    label = { Text("Sender") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.bodyInput,
                    onValueChange = onBodyChange,
                    label = { Text("SMS body") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = onRun,
                    enabled = state.bodyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run parsers")
                }
            }
            if (state.hasRun) {
                items(state.results) { result ->
                    ParserResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun ParserResultCard(result: ParserMatchResultUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (result.matched) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            imageVector = if (result.matched) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (result.matched) "Matched" else "No match",
            tint = if (result.matched) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Parser playground - Light", showBackground = true)
@Composable
private fun ParserPlaygroundLightPreview() {
    Sms2WalletTheme(darkTheme = false, useDynamicColor = false) {
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
    Sms2WalletTheme(darkTheme = true, useDynamicColor = false) {
        ParserPlaygroundContent(
            state = SampleData.parserPlayground,
            onBack = {},
            onSenderChange = {},
            onBodyChange = {},
            onRun = {}
        )
    }
}
