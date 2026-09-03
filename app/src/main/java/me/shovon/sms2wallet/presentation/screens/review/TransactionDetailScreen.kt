package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shovon.sms2wallet.presentation.components.FormErrorSummary
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.TransactionEditForm
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons

/**
 * The final review step for one parsed transaction before it is queued for Wallet.
 *
 * Reads top to bottom as the questions a user actually asks:
 *  1. *What am I looking at?* - the provenance header naming the source, account and time, with
 *     the original SMS underneath so the parse can be checked against the text it came from.
 *  2. *Does anything need me?* - an attention banner, shown only when a flag is set.
 *  3. *Is it right, and where does it go?* - the editable form.
 *  4. *What do I do now?* - a pinned action bar, so the primary action is reachable without
 *     scrolling to the end of the form and never competes with the top bar's back button.
 */
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = state.isSaving && state.id == null

    Sms2WalletScaffold(
        title = "Review transaction",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(PhosphorIcons.ArrowBack, contentDescription = "Back")
            }
        },
        bottomBar = {
            if (!isLoading && state.id != null) {
                ReviewActionBar(
                    isSaving = state.isSaving,
                    onPush = { viewModel.save(onSaved) },
                    onDismiss = { viewModel.dismiss(onSaved) }
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .wrapContentSize()
            ) {
                CircularProgressIndicator()
            }
            return@Sms2WalletScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            ProvenanceHeader(state)

            if (state.needsAttention) {
                AttentionBanner(state)
            }

            state.errorMessage?.let { message ->
                FormErrorSummary(message)
            }

            TransactionEditForm(
                state = state,
                onMerchantChange = viewModel::onMerchantChange,
                onAmountChange = viewModel::onAmountChange,
                onDirectionChange = viewModel::onDirectionChange,
                onCategoryChange = viewModel::onCategoryChange,
                onAccountChange = viewModel::onAccountChange,
                onNoteChange = viewModel::onNoteChange
            )
        }
    }
}

/**
 * Names the transaction being reviewed and shows the SMS it was parsed from.
 *
 * Typographic rather than boxed: this is the screen's subject, so it sits directly on the
 * background at the highest type size and lets the grouped fields below read as its detail.
 */
@Composable
private fun ProvenanceHeader(state: TransactionDetailUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.merchant.ifBlank { state.providerName ?: "Transaction" },
            style = MaterialTheme.typography.headlineSmall
        )
        state.sourceSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
        state.smsPreview?.let { sms ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SMS_QUOTE_ALPHA)
            ) {
                Row(modifier = Modifier.padding(Spacing.md)) {
                    // A thin rule marks this as quoted source text rather than app copy.
                    Surface(
                        modifier = Modifier.size(width = SMS_QUOTE_RULE_WIDTH, height = SMS_QUOTE_RULE_HEIGHT),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {}
                    Text(
                        text = sms,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.md)
                    )
                }
            }
        }
    }
}

/** Warning surface listing exactly why this transaction was flagged. */
@Composable
private fun AttentionBanner(state: TransactionDetailUiState) {
    val extended = Sms2WalletTheme.extendedColors
    val reason = when {
        state.isSuspectedDuplicate ->
            "This looks like a transaction you already have. Pushing it may create a duplicate in Wallet."
        else ->
            "The last send attempt's outcome is unknown. Check Wallet before pushing again."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = extended.warningContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = PhosphorIcons.WarningAmber,
                contentDescription = null,
                tint = extended.onWarningContainer,
                modifier = Modifier.size(IconSize.md)
            )
            Column {
                Text(
                    text = if (state.isSuspectedDuplicate) "Possible duplicate" else "Needs verification",
                    style = MaterialTheme.typography.titleSmall,
                    color = extended.onWarningContainer
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.onWarningContainer,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            }
        }
    }
}

/**
 * Pinned action bar. One filled primary action, one low-emphasis destructive one - so "push"
 * is unmistakably the thing to do and "dismiss" is available without being easy to hit.
 */
@Composable
private fun ReviewActionBar(isSaving: Boolean, onPush: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onPush,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.md),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Push to Wallet")
                    }
                }
            }
        }
    }
}

private const val SMS_QUOTE_ALPHA = 0.35f
private val SMS_QUOTE_RULE_WIDTH = 3.dp
private val SMS_QUOTE_RULE_HEIGHT = 40.dp
