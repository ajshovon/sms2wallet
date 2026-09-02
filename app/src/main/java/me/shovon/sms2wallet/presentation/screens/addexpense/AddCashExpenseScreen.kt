package me.shovon.sms2wallet.presentation.screens.addexpense

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.shovon.sms2wallet.presentation.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import me.shovon.sms2wallet.presentation.components.FormErrorSummary
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.TransactionEditForm
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/**
 * Manual "cash expense" entry sheet, opened from the Dashboard FAB - for spending that never
 * generates an SMS (cash purchases, etc.).
 */
@Composable
fun AddCashExpenseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddCashExpenseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Sms2WalletScaffold(
        title = "Add cash expense",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
        },
        actions = {
            TextButton(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.isSaving && state.amountText.isNotBlank()
            ) {
                Text("Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            state.errorMessage?.let { FormErrorSummary(it) }

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
