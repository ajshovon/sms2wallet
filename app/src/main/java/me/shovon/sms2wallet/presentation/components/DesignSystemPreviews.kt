package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.domain.model.AccentColor

/**
 * A gallery of every shared component and the states it can be in.
 *
 * The point is that states are designed together rather than discovered one at a time on
 * whichever screen happens to hit them: an error field, a disabled picker and a filled one all
 * render side by side here, so a difference in height, label colour or border weight is obvious
 * before it ships. Rendering the same gallery in both themes catches the contrast problems that
 * only appear on a dark surface.
 *
 * Preview-only; nothing here is referenced by the app.
 */
@Composable
private fun ComponentGallery() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            SectionHeader(
                title = "Text fields",
                supportingText = "Label above, supporting line reserved so errors never shift layout.",
                trailing = { TextButton(onClick = {}) { Text("Action") } }
            )

            AppTextField(label = "Empty", value = "", onValueChange = {}, placeholder = "Placeholder")
            AppTextField(label = "Filled", value = "SHWAPNO SUPERSHOP", onValueChange = {})
            AppTextField(
                label = "With helper",
                value = "",
                onValueChange = {},
                placeholder = "0.00",
                prefix = "৳",
                supportingText = "Helper text sits in the reserved line."
            )
            AppTextField(
                label = "Error",
                value = "0",
                onValueChange = {},
                prefix = "৳",
                errorText = "Enter an amount greater than zero."
            )
            AppTextField(
                label = "Optional",
                value = "",
                onValueChange = {},
                placeholder = "Anything worth remembering",
                isOptional = true
            )
            AppTextField(label = "Disabled", value = "Not editable", onValueChange = {}, enabled = false)

            SectionHeader(title = "Pickers")

            PickerField(
                label = "Unset",
                value = null,
                options = listOf("Cash", "bKash"),
                onSelect = {},
                placeholder = "Choose an account"
            )
            PickerField(
                label = "Selected",
                value = "bKash Wallet",
                options = listOf("Cash", "bKash Wallet"),
                onSelect = {}
            )
            PickerField(
                label = "Error",
                value = null,
                options = listOf("Cash"),
                onSelect = {},
                placeholder = "Choose an account",
                errorText = "Pick an account - there is nowhere to push this yet."
            )
            PickerField(
                label = "Disabled",
                value = "Cash",
                options = listOf("Cash"),
                onSelect = {},
                enabled = false
            )

            SectionHeader(title = "Badges and amounts")

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusBadge(text = "Not mapped", intent = BadgeIntent.WARNING)
                StatusBadge(text = "Needs verification", intent = BadgeIntent.INFO)
                StatusBadge(text = "Neutral", intent = BadgeIntent.NEUTRAL)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MoneyText(
                    amount = BigDecimal("3275.50"),
                    direction = TransactionDirection.EXPENSE,
                    style = MaterialTheme.typography.titleMedium
                )
                MoneyText(
                    amount = BigDecimal("45000.00"),
                    direction = TransactionDirection.INCOME,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            SectionHeader(title = "Grouped rows", supportingText = "One container per group, not per row.")

            GroupedContainer {
                GalleryRow("First row", "Top corners rounded")
                GroupedRowDivider()
                GalleryRow("Middle row", "Square corners, inset divider")
                GroupedRowDivider()
                GalleryRow("Last row", "Bottom corners rounded")
            }

            SectionHeader(title = "Form error summary")
            FormErrorSummary("This transaction no longer exists.")
        }
    }
}

@Composable
private fun GalleryRow(title: String, supporting: String) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs)
        )
    }
}

@Preview(name = "Components - Light", showBackground = true, heightDp = 1900)
@Composable
private fun ComponentGalleryLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) { ComponentGallery() }
}

@Preview(name = "Components - Dark", showBackground = true, heightDp = 1900)
@Composable
private fun ComponentGalleryDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) { ComponentGallery() }
}

/** Small-width check: labels, badges and amounts must not clip on a 320dp device. */
@Preview(name = "Components - Small width", showBackground = true, widthDp = 320, heightDp = 1900)
@Composable
private fun ComponentGallerySmallWidthPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) { ComponentGallery() }
}

/** Largest system font: the reserved supporting line must still hold one line of text. */
@Preview(name = "Components - Large font", showBackground = true, fontScale = 1.5f, heightDp = 2400)
@Composable
private fun ComponentGalleryLargeFontPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) { ComponentGallery() }
}

@Preview(name = "Empty state", showBackground = true, heightDp = 400)
@Composable
private fun EmptyStatePreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        EmptyState(
            icon = PhosphorIcons.Inbox,
            title = "Nothing to review",
            description = "New transactions parsed from your SMS show up here so you can check them before they reach Wallet.",
            modifier = Modifier.padding(0.dp)
        )
    }
}
