package me.shovon.sms2wallet.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.PickerField
import me.shovon.sms2wallet.presentation.model.ConnectionStatus
import me.shovon.sms2wallet.presentation.model.IntelligenceUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Spacing

private const val API_KEY_URL = "https://aistudio.google.com/apikey"

/**
 * "Intelligence" settings: the Gemini key, the model, and what is shared with it.
 *
 * The sharing switches sit directly under the key rather than behind a sub-screen, because they
 * are the part of this feature a user is entitled to be suspicious about. Each switch says what
 * happens when it is off, so turning something off is a decision with a known consequence rather
 * than a guess about whether the feature will still work.
 */
@Composable
fun IntelligenceSection(
    state: IntelligenceUiState,
    onKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onTestKey: () -> Unit,
    onClearKey: () -> Unit,
    onModelChange: (String) -> Unit,
    onShareCategoryNamesChange: (Boolean) -> Unit,
    onShareAccountNamesChange: (Boolean) -> Unit,
    onDefaultAccountChange: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    GroupedContainer {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            AppTextField(
                label = "Gemini API key",
                value = state.apiKeyInput,
                onValueChange = onKeyChange,
                placeholder = if (state.hasStoredKey) {
                    "Enter a new key to replace the saved one"
                } else {
                    "Paste your Gemini API key"
                },
                supportingText = if (state.hasStoredKey && state.apiKeyInput.isEmpty()) {
                    "A key is saved on this device, encrypted."
                } else {
                    null
                },
                visualTransformation = if (state.isKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onToggleKeyVisibility) {
                        Icon(
                            imageVector = if (state.isKeyVisible) {
                                SolarIcons.VisibilityOff
                            } else {
                                SolarIcons.Visibility
                            },
                            contentDescription = if (state.isKeyVisible) "Hide key" else "Show key"
                        )
                    }
                }
            )

            KeyStatusRow(status = state.status)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onTestKey,
                    enabled = !state.isTesting && (state.apiKeyInput.isNotBlank() || state.hasStoredKey),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.md),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test key")
                    }
                }
                if (state.hasStoredKey) {
                    TextButton(onClick = onClearKey) { Text("Remove") }
                }
            }

            TextButton(
                onClick = { uriHandler.openUri(API_KEY_URL) },
                modifier = Modifier.padding(top = Spacing.xxs)
            ) {
                Text("Get a free API key")
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            PickerField(
                label = "Model",
                value = state.model,
                options = state.modelOptions,
                onSelect = onModelChange,
                supportingText = "Flash models are fast and free-tier friendly."
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = "Shared with Google",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Your phrase and the current date always go with the request. " +
                    "These add context so it can fill in more for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.sm)
            )

            ShareToggleRow(
                title = "Category names",
                description = if (state.shareCategoryNames) {
                    "The model picks from your own category list."
                } else {
                    "Categories stay on this device and are matched here from the merchant name."
                },
                checked = state.shareCategoryNames,
                onCheckedChange = onShareCategoryNamesChange
            )

            ShareToggleRow(
                title = "Account names",
                description = if (state.shareAccountNames) {
                    "Saying \"on bkash\" selects that account."
                } else {
                    "Account names never leave this device. The default below is used instead."
                },
                checked = state.shareAccountNames,
                onCheckedChange = onShareAccountNamesChange,
                modifier = Modifier.padding(top = Spacing.sm)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            PickerField(
                label = "Default account",
                value = state.defaultAccountName,
                options = state.availableAccountNames,
                onSelect = onDefaultAccountChange,
                placeholder = "Select an account",
                supportingText = "Pre-selected for every new transaction, typed or manual.",
                enabled = state.availableAccountNames.isNotEmpty()
            )
        }
    }
}

/** A switch plus the consequence of turning it off, which is the part that is hard to guess. */
@Composable
private fun ShareToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KeyStatusRow(status: ConnectionStatus) {
    val (message, color) = when (status) {
        ConnectionStatus.NotTested -> return
        ConnectionStatus.Success -> "Key works." to MaterialTheme.colorScheme.primary
        is ConnectionStatus.Syncing -> "Google is still preparing this key." to
            MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Failed -> status.message to MaterialTheme.colorScheme.error
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}
