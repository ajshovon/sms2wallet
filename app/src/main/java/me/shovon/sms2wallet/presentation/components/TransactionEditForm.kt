package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection

/**
 * Shared form used by both the transaction detail/edit sheet (Review queue) and the
 * add-cash-expense sheet: amount, expense/income toggle, merchant, category, account, note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditForm(
    state: TransactionDetailUiState,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (TransactionDirection) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.amountText,
            onValueChange = onAmountChange,
            label = { Text("Amount (৳)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TransactionDirection.entries.forEachIndexed { index, direction ->
                SegmentedButton(
                    selected = state.direction == direction,
                    onClick = { onDirectionChange(direction) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TransactionDirection.entries.size)
                ) {
                    Text(if (direction == TransactionDirection.EXPENSE) "Expense" else "Income")
                }
            }
        }

        OutlinedTextField(
            value = state.merchant,
            onValueChange = onMerchantChange,
            label = { Text("Merchant") },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownField(
            label = "Category",
            selected = state.category,
            options = state.availableCategories,
            onSelected = onCategoryChange
        )

        DropdownField(
            label = "Account",
            selected = state.accountName,
            options = state.availableAccounts,
            onSelected = onAccountChange
        )

        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
