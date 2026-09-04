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
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.ProviderAvatar
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.ParserSettingUiState
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * One provider row in the "Parsers" settings section: distinct provider avatar branding,
 * two independent switches (Enabled, Auto-push) plus a caption naming the mapped Wallet account,
 * or a warning "Not mapped" state.
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
                titleStyle = MaterialTheme.typography.titleMedium,
                titleWeight = FontWeight.SemiBold,
                checked = setting.isEnabled,
                onCheckedChange = onEnabledChange,
                leading = {
                    ProviderAvatar(providerName = setting.providerName, size = 36.dp)
                }
            )

            SettingToggleRow(
                title = "Auto-push to Wallet",
                titleStyle = MaterialTheme.typography.bodyMedium,
                titleWeight = FontWeight.Normal,
                checked = setting.isAutoPushEnabled && setting.isMapped,
                enabled = setting.isEnabled && setting.isMapped,
                onCheckedChange = onAutoPushChange,
                modifier = Modifier.padding(start = 48.dp),
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
 * Label-plus-switch row with an optional leading avatar and supporting text beneath the label.
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
    leading: @Composable (() -> Unit)? = null,
    supporting: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        leading?.invoke()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            supporting?.invoke()
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
