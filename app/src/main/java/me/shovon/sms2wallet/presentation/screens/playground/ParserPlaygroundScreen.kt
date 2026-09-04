package me.shovon.sms2wallet.presentation.screens.playground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.GroupedRowDivider
import me.shovon.sms2wallet.presentation.components.ProviderAvatar
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.ParserMatchResultUiState
import me.shovon.sms2wallet.presentation.model.ParserPlaygroundUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing

private data class SampleSmsTemplate(
    val label: String,
    val sender: String,
    val body: String
)

private val SAMPLE_TEMPLATES = listOf(
    SampleSmsTemplate(
        label = "bKash Receive",
        sender = "bKash",
        body = "You have received Tk 650.00 from 01700000000. Fee Tk 0.00. Balance Tk 12,340.00. TrxID 9H7B3A2C1 at 03/09/2026 10:24"
    ),
    SampleSmsTemplate(
        label = "bKash Payment",
        sender = "bKash",
        body = "Payment Tk 1,250.00 to SHWAPNO successful. Fee Tk 0.00. Balance Tk 11,090.00. TrxID 9H7B3A2C2 at 03/09/2026 12:30"
    ),
    SampleSmsTemplate(
        label = "Nagad Cash In",
        sender = "Nagad",
        body = "Cash In Tk 2,000.00 from 01900000000 is successful. Comm: Tk 0.00. Balance: Tk 5,420.00. TxnID: 72N39A2B at 03/09/2026 15:15"
    ),
    SampleSmsTemplate(
        label = "City Bank Debit",
        sender = "CityBank",
        body = "Your A/C ending 1234 has been debited by BDT 3,500.00 on 03-SEP-26 at UNIMART. Avail Bal BDT 45,230.00"
    ),
    SampleSmsTemplate(
        label = "BRAC Bank Card",
        sender = "BRAC BANK",
        body = "Dear Cardholder, your credit card 4321 is charged BDT 850.00 at AARONG on 03-SEP-2026. Avail Limit BDT 80,000.00"
    )
)

/**
 * Parser playground: paste an SMS sender + body or tap a sample template to see which
 * registered parsers match it and every extracted field.
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
                Icon(SolarIcons.ArrowBack, contentDescription = "Back")
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
            // Quick sample templates row
            item(key = "samples") {
                Column(modifier = Modifier.padding(bottom = Spacing.md)) {
                    Text(
                        text = "Try sample messages",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        SAMPLE_TEMPLATES.forEach { template ->
                            SampleTemplateChip(
                                label = template.label,
                                onClick = {
                                    onSenderChange(template.sender)
                                    onBodyChange(template.body)
                                }
                            )
                        }
                    }
                }
            }

            item(key = "sender") {
                AppTextField(
                    label = "Sender",
                    value = state.senderInput,
                    onValueChange = onSenderChange,
                    placeholder = "e.g. bKash, 16247, CityBank"
                )
            }

            item(key = "body") {
                AppTextField(
                    label = "SMS body",
                    value = state.bodyInput,
                    onValueChange = onBodyChange,
                    placeholder = "Paste the full transaction SMS here",
                    singleLine = false
                )
            }

            item(key = "run") {
                Button(
                    onClick = onRun,
                    enabled = state.bodyInput.isNotBlank() && !state.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.md),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Parsing…")
                    } else {
                        Icon(
                            imageVector = SolarIcons.Science,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.md)
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Run parsers")
                    }
                }
            }

            if (state.hasRun) {
                val matchedCount = state.results.count { it.matched }
                item(key = "results-header") {
                    SectionHeader(
                        title = "Results ($matchedCount matched)",
                        supportingText = if (matchedCount > 0) {
                            "Successfully extracted transaction fields below"
                        } else {
                            "No provider parser matched this message"
                        },
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                }

                // Show matched results first
                val sortedResults = state.results.sortedByDescending { it.matched }
                itemsIndexed(sortedResults, key = { _, r -> r.providerName }) { index, result ->
                    if (index > 0) GroupedRowDivider()
                    ParserResultCard(result = result, index = index, count = sortedResults.size)
                }
            }
        }
    }
}

@Composable
private fun SampleTemplateChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )
    }
}

@Composable
private fun ParserResultCard(result: ParserMatchResultUiState, index: Int, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedRowShape(index = index, count = count),
        color = if (result.matched) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            groupedSurfaceColor()
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ProviderAvatar(providerName = result.providerName, size = 32.dp)

                Text(
                    text = result.providerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (result.matched) SolarIcons.CheckCircle else SolarIcons.Cancel,
                    contentDescription = if (result.matched) "Matched" else "No match",
                    tint = if (result.matched) {
                        Sms2WalletTheme.extendedColors.income
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(IconSize.md)
                )
            }

            if (result.matched) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    result.extractedFields.forEach { field ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = field.value,
                                style = if (field.label.contains("Amount", ignoreCase = true)) {
                                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                } else if (field.label.contains("ID", ignoreCase = true) || field.label.contains("Account", ignoreCase = true)) {
                                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = if (field.label.contains("Amount", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            } else if (result.failureReason != null) {
                Text(
                    text = result.failureReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            }
        }
    }
}

@Preview(name = "Parser playground - Light", showBackground = true)
@Composable
private fun ParserPlaygroundLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
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
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) {
        ParserPlaygroundContent(
            state = SampleData.parserPlayground,
            onBack = {},
            onSenderChange = {},
            onBodyChange = {},
            onRun = {}
        )
    }
}
