package me.shovon.sms2wallet.presentation.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.util.MoneyFormatter
import java.math.BigDecimal

/**
 * Renders a BDT amount formatted via [MoneyFormatter], coloured green for income and the
 * theme's expense colour for expenses (see [Sms2WalletTheme.extendedColors]).
 */
@Composable
fun MoneyText(
    amount: BigDecimal,
    direction: TransactionDirection,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val extendedColors = Sms2WalletTheme.extendedColors
    val color = when (direction) {
        TransactionDirection.INCOME -> extendedColors.income
        TransactionDirection.EXPENSE -> extendedColors.expense
    }
    val prefix = when (direction) {
        TransactionDirection.INCOME -> "+"
        TransactionDirection.EXPENSE -> "-"
    }
    Text(
        text = prefix + MoneyFormatter.formatBdt(amount),
        modifier = modifier,
        color = color,
        style = style
    )
}
