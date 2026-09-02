package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.util.MoneyFormatter

/**
 * Shared editable form for a transaction, used by the review sheet and the add-cash-expense
 * sheet.
 *
 * Ordered by how much each field matters rather than by how the data happens to be stored:
 * the amount and its direction come first and are typographically the largest thing on screen,
 * then where the money goes (category, account), then the optional note. Every control is built
 * from [AppTextField]/[PickerField], so labels, fills, borders, heights and error treatment are
 * identical across the two sheets.
 *
 * Form-level errors are rendered by the host screen above this form, not here, so a failed save
 * shows one summary rather than the same sentence twice.
 */
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppTextField(
            label = "Amount",
            value = state.amountText,
            onValueChange = onAmountChange,
            placeholder = "0.00",
            prefix = MoneyFormatter.TAKA_SYMBOL,
            errorText = state.amountError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            // The amount is the number the user is really checking, so it is set in a heavier,
            // larger style than the rest of the form.
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        )

        FieldScaffold(label = "Type") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionDirection.entries.forEachIndexed { index, direction ->
                    SegmentedButton(
                        selected = state.direction == direction,
                        onClick = { onDirectionChange(direction) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionDirection.entries.size
                        )
                    ) {
                        Text(if (direction == TransactionDirection.EXPENSE) "Expense" else "Income")
                    }
                }
            }
        }

        AppTextField(
            label = "Merchant",
            value = state.merchant,
            onValueChange = onMerchantChange,
            placeholder = "Who was paid"
        )

        PickerField(
            label = "Category",
            value = state.category,
            options = state.availableCategories,
            onSelect = onCategoryChange,
            placeholder = "Choose a category",
            searchPlaceholder = "Search categories",
            supportingText = if (state.availableCategories.isEmpty()) {
                "Sync your Wallet account in Settings to load categories."
            } else {
                null
            }
        )

        PickerField(
            label = "Account",
            value = state.accountName,
            options = state.availableAccounts,
            onSelect = onAccountChange,
            placeholder = "Choose an account",
            searchPlaceholder = "Search accounts",
            errorText = state.accountError
        )

        AppTextField(
            label = "Note",
            value = state.note,
            onValueChange = onNoteChange,
            placeholder = "Anything worth remembering",
            isOptional = true,
            singleLine = false
        )
    }
}
