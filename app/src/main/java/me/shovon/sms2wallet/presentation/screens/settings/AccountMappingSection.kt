package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.PickerControl
import me.shovon.sms2wallet.presentation.components.ProviderAvatar
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.AccountMappingRowUiState
import me.shovon.sms2wallet.presentation.theme.Spacing
import androidx.compose.ui.unit.dp

/**
 * One detected-source row in "Account mapping": which Wallet account transactions from this
 * SMS source should land in.
 *
 * The picker is stacked *under* the source name rather than beside it. Side by side, the
 * control was squeezed into a fixed 180dp column that truncated every real account name, and
 * the source label, a status badge and the control competed for one line. Stacked, the source
 * name acts as the picker's label (so no per-row "Wallet account" caption is needed) and the
 * control gets the full row width.
 */
@Composable
fun AccountMappingRow(
    mapping: AccountMappingRowUiState,
    onAccountSelected: (String) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    val isMapped = mapping.mappedWalletAccountName != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = groupedRowShape(index = index, count = count),
        color = groupedSurfaceColor()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ProviderAvatar(providerName = mapping.sourceLabel, size = 36.dp)
                Text(
                    text = mapping.sourceLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!isMapped) {
                    StatusBadge(text = "Not mapped", intent = BadgeIntent.WARNING)
                }
            }

            PickerControl(
                value = mapping.mappedWalletAccountName,
                options = mapping.availableWalletAccountNames,
                onSelect = onAccountSelected,
                placeholder = "Choose a Wallet account",
                sheetTitle = "Wallet account",
                searchPlaceholder = "Search accounts",
                // The visible source name labels this control; a screen reader reaching the
                // picker alone would otherwise hear only the chosen value with no context.
                contentDescription = "Wallet account for ${mapping.sourceLabel}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
            )

            if (!isMapped) {
                Text(
                    text = "Transactions from this source stay in the review queue until it's mapped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        }
    }
}
