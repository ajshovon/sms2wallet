package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.model.ReminderSettingsUiState

/**
 * "Reminders" settings section: daily reminder on/off, a time picker, and a stepper for
 * "skip if I already logged N today".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSection(
    state: ReminderSettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onSkipCountChange: (Int) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily reminder", style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.isEnabled, onCheckedChange = onEnabledChange)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remind me at",
                    color = if (state.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showTimePicker = true }, enabled = state.isEnabled) {
                    Text(formatTime(state.hourOfDay, state.minute))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Skip if I already logged ${state.skipIfAlreadyLoggedCount} today",
                    color = if (state.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onSkipCountChange((state.skipIfAlreadyLoggedCount - 1).coerceAtLeast(0)) },
                        enabled = state.isEnabled
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                    }
                    Text(state.skipIfAlreadyLoggedCount.toString(), style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { onSkipCountChange((state.skipIfAlreadyLoggedCount + 1).coerceAtMost(20)) },
                        enabled = state.isEnabled
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase")
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.hourOfDay,
            initialMinute = state.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

private fun formatTime(hourOfDay: Int, minute: Int): String {
    val period = if (hourOfDay < 12) "AM" else "PM"
    val hour12 = when (val h = hourOfDay % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(hour12, minute, period)
}
