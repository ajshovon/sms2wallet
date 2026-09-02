package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.TransactionEditForm
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/**
 * Edit sheet for one parsed-but-unpushed transaction, reached by tapping a Review-queue card.
 * Lets the user correct the amount/merchant/category/account before it is queued for push.
 */
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Sms2WalletScaffold(
        title = "Edit transaction",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            TextButton(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.isSaving && state.amountText.isNotBlank()
            ) {
                Text("Push")
            }
        }
    ) { padding ->
        if (state.isSaving && state.id == null) {
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
                .padding(16.dp)
        ) {
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
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
