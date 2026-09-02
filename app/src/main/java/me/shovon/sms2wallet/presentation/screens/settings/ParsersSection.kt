package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.model.ParserSettingUiState

/**
 * One provider row in the "Parsers" settings section: two independent switches (Enabled,
 * Auto-push) plus a caption naming the mapped Wallet account, or a warning-styled
 * "Not mapped" caption. Auto-push is disabled and captioned when there's no mapping, since
 * it has no effect without one.
 */
@Composable
fun ParserSettingRow(
    setting: ParserSettingUiState,
    onEnabledChange: (Boolean) -> Unit,
    onAutoPushChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(setting.providerName, style = MaterialTheme.typography.titleMedium)
                LabeledSwitch(label = "Enabled", checked = setting.isEnabled, onCheckedChange = onEnabledChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (setting.isMapped) {
                        Text(
                            text = "Mapped to ${setting.mappedAccountName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        StatusBadge(text = "Not mapped", intent = BadgeIntent.WARNING)
                    }
                    if (!setting.isMapped) {
                        Text(
                            text = "Auto-push needs an account mapping first",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LabeledSwitch(
                    label = "Auto-push",
                    checked = setting.isAutoPushEnabled && setting.isMapped,
                    enabled = setting.isMapped,
                    onCheckedChange = onAutoPushChange
                )
            }
        }
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
