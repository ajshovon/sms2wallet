package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons

/**
 * Field chrome shared by every input in the app: a persistent label *above* the control, the
 * control itself, and a supporting/error line beneath it.
 *
 * The label sits outside the control rather than using Material's floating label on purpose.
 * A floating label doubles as the placeholder, so a filled field loses its own description and
 * a picker (which has no text cursor) reads as an unlabelled value. Hoisting it means the
 * question a field answers is always visible, and text inputs and pickers line up on the same
 * baseline grid instead of each inventing their own.
 *
 * [FieldScaffold] owns the supporting line so its height is reserved whether or not a message
 * is showing - an error appearing on submit must not shove the rest of the form downwards.
 */
@Composable
fun FieldScaffold(
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    isOptional: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val labelColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
        errorText != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor
            )
            if (isOptional) {
                Text(
                    text = "Optional",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(modifier = Modifier.padding(top = Spacing.sm)) { content() }

        SupportingLine(supportingText = supportingText, errorText = errorText, enabled = enabled)
    }
}

/**
 * The message slot under a field. Always occupies [SUPPORTING_LINE_MIN_HEIGHT] so validation
 * text can appear and disappear without moving anything below it.
 */
@Composable
private fun SupportingLine(supportingText: String?, errorText: String?, enabled: Boolean) {
    val message = errorText ?: supportingText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .heightIn(min = SUPPORTING_LINE_MIN_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        if (message == null) return@Row
        if (errorText != null) {
            Icon(
                imageVector = PhosphorIcons.ErrorOutline,
                // The message beside it already states the problem; announcing the glyph too
                // would just make a screen reader say it twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(IconSize.sm)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                errorText != null -> MaterialTheme.colorScheme.error
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Standard single-line text input. Wraps [FieldScaffold], so it shares the label placement,
 * supporting-line height and error treatment with [PickerField] and every other input.
 */
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    isOptional: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    prefix: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    FieldScaffold(
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
        isOptional = isOptional,
        enabled = enabled
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            isError = errorText != null,
            singleLine = singleLine,
            textStyle = textStyle,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            shape = MaterialTheme.shapes.medium,
            prefix = prefix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                // A focused field should read as focused from across the room: the container
                // tints as well as the border, so focus is not carried by a 2dp outline alone.
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FOCUSED_FILL_ALPHA),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RESTING_FILL_ALPHA),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DISABLED_FILL_ALPHA),
                errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = RESTING_FILL_ALPHA)
            )
        )
    }
}

/**
 * Form-level failure summary, placed above the fields so it is read before scrolling.
 *
 * Complements - never replaces - the inline errors on individual fields: the summary says the
 * save failed, the field says which value is wrong.
 */
@Composable
fun FormErrorSummary(message: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = PhosphorIcons.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(IconSize.md)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** Material's own disabled emphasis, applied to label and supporting text alike. */
internal const val DISABLED_ALPHA = 0.38f

private const val FOCUSED_FILL_ALPHA = 0.55f
private const val RESTING_FILL_ALPHA = 0.30f
private const val DISABLED_FILL_ALPHA = 0.12f

/** One line of `bodySmall` plus its descender, so the slot never resizes when text appears. */
private val SUPPORTING_LINE_MIN_HEIGHT = 18.dp
