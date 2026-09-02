package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

/** Semantic intent for a small pill-shaped status label. */
enum class BadgeIntent {
    WARNING,
    INFO,
    NEUTRAL
}

/**
 * Small pill-shaped label used for "Suspected duplicate", "Needs verification", and
 * "Not mapped" indicators across the Review queue and Settings screens.
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    intent: BadgeIntent = BadgeIntent.NEUTRAL
) {
    val extended = Sms2WalletTheme.extendedColors
    val (container: Color, content: Color) = when (intent) {
        BadgeIntent.WARNING -> extended.warningContainer to extended.onWarningContainer
        BadgeIntent.INFO -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BadgeIntent.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        modifier = modifier
            .background(color = container, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = content,
        style = MaterialTheme.typography.labelSmall
    )
}
