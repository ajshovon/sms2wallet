package me.shovon.sms2wallet.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii, as one source of truth.
 *
 * [CornerRadius.group] is exposed as a raw value as well as through [AppShapes] because grouped
 * list rows compute their own per-position shape (only the first row's top corners and the last
 * row's bottom corners are rounded). Previously that computation carried its own private `16.dp`
 * constant, so a container and the rows inside it derived the same radius from two places and
 * could drift apart.
 */
object CornerRadius {
    /** Chips and badges. */
    val badge = 8.dp
    /** Inputs, pickers, and inline banners. */
    val field = 12.dp
    /** Grouped containers and list-row groups. */
    val group = 16.dp
}

/**
 * Material 3 shape scale for the app. Only the steps the app actually uses are overridden; the
 * rest stay at their Material defaults so an unlisted component still looks native.
 */
val AppShapes = Shapes(
    small = RoundedCornerShape(CornerRadius.badge),
    medium = RoundedCornerShape(CornerRadius.field),
    large = RoundedCornerShape(CornerRadius.group),
)
