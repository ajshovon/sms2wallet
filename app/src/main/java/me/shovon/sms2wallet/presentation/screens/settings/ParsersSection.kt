package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.ParserSettingUiState
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * One provider row in the "Parsers" settings section: two independent switches (Enabled,
 * Auto-push) plus a caption naming the mapped Wallet account, or a warning "Not mapped" state.
 *
 * Auto-push is disabled and explained when there is no mapping, since it cannot do anything
 * without one - a switch that silently does nothing is worse than one that says why it can't.
 */
@Composable
fun ParserSettingRow(
    setting: ParserSettingUiState,
    onEnabledChange: (Boolean) -> Unit,
    onAutoPushChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = groupedRowShape(index = index, count = count),
        color = groupedSurfaceColor()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SettingToggleRow(
                title = setting.providerName,
                titleStyle = MaterialTheme.typography.bodyLarge,
                titleWeight = FontWeight.Medium,
                checked = setting.isEnabled,
                onCheckedChange = onEnabledChange
            )

            SettingToggleRow(
                title = "Auto-push",
                titleStyle = MaterialTheme.typography.bodyMedium,
                titleWeight = FontWeight.Normal,
                checked = setting.isAutoPushEnabled && setting.isMapped,
                enabled = setting.isEnabled && setting.isMapped,
                onCheckedChange = onAutoPushChange,
                supporting = {
                    when {
                        !setting.isMapped -> Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = Spacing.xxs)
                        ) {
                            StatusBadge(text = "Not mapped", intent = BadgeIntent.WARNING)
                            Text(
                                text = "Map an account first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> Text(
                            text = "Goes straight to ${setting.mappedAccountName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs)
                        )
                    }
                }
            )
        }
    }
}

/**
 * Label-plus-switch row with an optional supporting line beneath the label. Shared by both
 * switches above so their alignment and disabled treatment cannot drift apart.
 */
@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleStyle: androidx.compose.ui.text.TextStyle,
    titleWeight: FontWeight,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            supporting?.invoke()
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
