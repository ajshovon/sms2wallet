package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MinTouchTarget
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * A single-select field that opens a full-width [SelectionSheet] instead of an anchored
 * dropdown menu.
 *
 * `ExposedDropdownMenuBox` was the wrong shape for this app: its menu is capped to the width
 * of its anchor (so a 180dp field truncated every account name), it has no room for a search
 * affordance, and its rows are shorter than Android's 48dp touch minimum. A sheet gets the full
 * screen width, can hold a search box for long lists, and gives each option a real touch target.
 *
 * Visually it matches [AppTextField] exactly - same label placement, fill, border, shape and
 * supporting line - so a form reads as one set of controls rather than a text input next to a
 * differently-shaped dropdown.
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    sheetTitle: String = label,
    searchPlaceholder: String = "Search $label"
) {
    FieldScaffold(
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
        enabled = enabled
    ) {
        PickerControl(
            value = value,
            options = options,
            onSelect = onSelect,
            placeholder = placeholder,
            isError = errorText != null,
            enabled = enabled,
            sheetTitle = sheetTitle,
            searchPlaceholder = searchPlaceholder,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The picker control on its own, without [FieldScaffold]'s label and supporting line.
 *
 * Used where the surrounding row already names what is being chosen - the account-mapping list
 * labels each picker with its SMS source, so a second "Wallet account" label above every row
 * would be pure repetition. Everywhere else prefer [PickerField], which keeps the label visible.
 */
@Composable
fun PickerControl(
    value: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    isError: Boolean = false,
    enabled: Boolean = true,
    sheetTitle: String = placeholder,
    searchPlaceholder: String = "Search",
    contentDescription: String? = null
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val hasValue = !value.isNullOrBlank()

    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = DISABLED_ALPHA)
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        hasValue -> MaterialTheme.colorScheme.onSurface
        // An unfilled picker reads as a prompt, not as a value the user already chose.
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .heightIn(min = FIELD_MIN_HEIGHT)
            .background(
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = FIELD_FILL_ALPHA)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = if (enabled) FIELD_FILL_ALPHA else DISABLED_FILL_ALPHA
                    )
                },
                shape = MaterialTheme.shapes.medium
            )
            .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.medium)
            .clickable(
                enabled = enabled,
                // Role.DropdownList tells a screen reader this opens a list of choices,
                // which a plain clickable Row would not convey.
                role = Role.DropdownList,
                onClick = { sheetOpen = true }
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = if (hasValue) value!! else placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
            },
            modifier = Modifier.size(IconSize.lg)
        )
    }

    if (sheetOpen) {
        SelectionSheet(
            title = sheetTitle,
            options = options,
            selected = value,
            searchPlaceholder = searchPlaceholder,
            onSelect = {
                onSelect(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false }
        )
    }
}

/**
 * Modal single-select list with search.
 *
 * Search appears once the list is long enough to be worth scanning ([SEARCH_THRESHOLD]); below
 * that it is pure clutter, since the whole list already fits on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionSheet(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    searchPlaceholder: String = "Search"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val showSearch = options.size >= SEARCH_THRESHOLD

    // derivedStateOf keeps the filter from re-running on every unrelated recomposition of the
    // sheet; it recomputes only when the query or the option list actually changes.
    val results by remember(options) {
        derivedStateOf {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) options
            else options.filter { it.contains(trimmed, ignoreCase = true) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)
            )

            if (showSearch) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = searchPlaceholder,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)
                )
            }

            when {
                options.isEmpty() -> SheetMessage(
                    icon = Icons.Filled.SearchOff,
                    title = "Nothing to choose from",
                    body = "Open Settings and tap Sync under \"Accounts and categories\" to pull " +
                        "these from Wallet."
                )

                results.isEmpty() -> SheetMessage(
                    icon = Icons.Filled.SearchOff,
                    title = "No matches for \"${query.trim()}\"",
                    body = "Try a shorter or differently spelled search.",
                    actionLabel = "Clear search",
                    onAction = { query = "" }
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results, key = { it }) { option ->
                        SelectionRow(
                            label = option,
                            isSelected = option == selected,
                            onClick = { onSelect(option) }
                        )
                    }
                }
            }
        }
    }
}

/** One option in a [SelectionSheet]. Full-width tap target, check mark on the current value. */
@Composable
private fun SelectionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // Selection is carried by weight and a check mark, not by the container tint alone,
            // so it survives both themes and colour-vision differences.
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(IconSize.md)
            )
        }
    }
}

/** Search input used inside [SelectionSheet]. Clear button appears only once there is a query. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.lg)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(IconSize.md)
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FIELD_FILL_ALPHA),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FIELD_FILL_ALPHA)
        )
    )
}

/** Shared empty/no-results block inside a sheet, so both states look like one component. */
@Composable
private fun SheetMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.xl)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) {
                androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Matches the height of an `OutlinedTextField`, so pickers and inputs align in a form. */
private val FIELD_MIN_HEIGHT = 56.dp

private const val FIELD_FILL_ALPHA = 0.30f
private const val DISABLED_FILL_ALPHA = 0.12f

/** Below this many options the whole list fits on screen and a search box is just noise. */
private const val SEARCH_THRESHOLD = 8
