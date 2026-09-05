package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.util.MoneyFormatter
import java.math.BigDecimal

private val QUICK_AMOUNT_INCREMENTS = listOf(50, 100, 500, 1000)

/**
 * Shared editable form for a transaction, used by the review sheet and the add-cash-expense
 * sheet.
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
    modifier: Modifier = Modifier,
    /** Null hides the suggestion affordance entirely, for callers with no AI configured. */
    onSuggestCategory: (() -> Unit)? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column {
            AppTextField(
                label = "Amount",
                value = state.amountText,
                onValueChange = onAmountChange,
                placeholder = "0.00",
                prefix = MoneyFormatter.TAKA_SYMBOL,
                errorText = state.amountError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall
            )

            // Quick increment chips for quick manual addition
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                QUICK_AMOUNT_INCREMENTS.forEach { inc ->
                    Surface(
                        onClick = {
                            val current = runCatching { BigDecimal(state.amountText.trim()) }.getOrNull() ?: BigDecimal.ZERO
                            val next = current.add(BigDecimal(inc))
                            val updated = if (next.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                                next.toBigInteger().toString()
                            } else {
                                next.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            }
                            onAmountChange(updated)
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .semantics {
                                contentDescription = "Add $inc Taka"
                            }
                    ) {
                        Box(
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        ) {
                            Text(
                                text = "+৳$inc",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        FieldScaffold(label = "Type") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionDirection.entries.forEachIndexed { index, direction ->
                    val isSelected = state.direction == direction
                    val selectedColor = if (direction == TransactionDirection.EXPENSE) {
                        Sms2WalletTheme.extendedColors.expense.copy(alpha = 0.15f)
                    } else {
                        Sms2WalletTheme.extendedColors.income.copy(alpha = 0.15f)
                    }
                    val selectedContentColor = if (direction == TransactionDirection.EXPENSE) {
                        Sms2WalletTheme.extendedColors.expense
                    } else {
                        Sms2WalletTheme.extendedColors.income
                    }

                    SegmentedButton(
                        selected = isSelected,
                        onClick = { onDirectionChange(direction) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionDirection.entries.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = selectedColor,
                            activeContentColor = selectedContentColor
                        )
                    ) {
                        Text(
                            text = if (direction == TransactionDirection.EXPENSE) "Expense" else "Income",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
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

        Column {
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

            // Sits under the field it fills, not beside the push action. Push writes to the
            // user's real Wallet and cannot be undone; nothing optional belongs next to it.
            if (onSuggestCategory != null && state.availableCategories.isNotEmpty()) {
                TextButton(
                    onClick = onSuggestCategory,
                    enabled = !state.isSuggestingCategory,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    modifier = Modifier.padding(top = Spacing.xxs)
                ) {
                    if (state.isSuggestingCategory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.sm),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text("Suggesting…")
                    } else {
                        Icon(
                            imageVector = SolarIcons.Science,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.sm)
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text("Suggest a category")
                    }
                }
            }

            state.suggestionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Spacing.sm, top = Spacing.xxs)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }

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
