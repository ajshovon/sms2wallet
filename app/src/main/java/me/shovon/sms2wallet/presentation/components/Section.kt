package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.theme.CornerRadius
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * Heading for a group of related content, with an optional trailing action.
 *
 * Deliberately typographic rather than another boxed element: a heading that sits directly on
 * the screen background reads as a level *above* the content it introduces, whereas a heading
 * inside its own card competes with the card below it.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        // Top-aligned, not centred: when the supporting text wraps to two lines a centred
        // trailing action drifts below the title it belongs to.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            }
        }
        // Nudged down so the action's label sits on the title's baseline rather than its cap line.
        trailing?.let {
            Row(modifier = Modifier.padding(top = Spacing.xxs)) { it() }
        }
    }
}

/**
 * One rounded surface holding several related rows, separated by [SectionDivider].
 *
 * This is the app's alternative to giving every row its own `Card`. A screen of stacked cards
 * reads as a list of unrelated islands with no grouping information; one container per *group*
 * says which rows belong together and cuts the number of competing edges and shadows down to
 * one per section.
 */
@Composable
fun GroupedContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTAINER_FILL_ALPHA)
    ) {
        Column(content = content)
    }
}

/**
 * Divider between rows of a [GroupedContainer], inset from the leading edge so the rows read as
 * a continuous list rather than separate blocks.
 */
@Composable
fun SectionDivider(modifier: Modifier = Modifier, startInset: androidx.compose.ui.unit.Dp = Spacing.lg) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = 1.dp,
        // outlineVariant is the one divider token that stays visible in both themes; a fixed
        // grey disappears against the dark surface.
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Divider between two grouped rows that are separate `LazyColumn` items.
 *
 * Unlike [SectionDivider] this paints the group's own background behind the rule, so a group
 * split across lazy items still reads as one unbroken surface. A bare divider there would show
 * the screen background through the seam and cut the group into pieces.
 */
@Composable
fun GroupedRowDivider(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = groupedSurfaceColor()) {
        HorizontalDivider(
            modifier = Modifier.padding(start = Spacing.lg),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * Shape for a row at [index] of [count] inside a grouped list.
 *
 * Lets a group be rendered as individual `items()` in a `LazyColumn` - staying lazy for long
 * lists - while still reading as one rounded container: only the first row's top corners and
 * the last row's bottom corners are rounded, and a single row gets all four.
 */
@Composable
fun groupedRowShape(index: Int, count: Int): androidx.compose.foundation.shape.CornerBasedShape {
    val radius = CornerRadius.group
    val none = androidx.compose.foundation.shape.CornerSize(0.dp)
    val full = androidx.compose.foundation.shape.CornerSize(radius)
    return androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (index == 0) full else none,
        topEnd = if (index == 0) full else none,
        bottomStart = if (index == count - 1) full else none,
        bottomEnd = if (index == count - 1) full else none
    )
}

/**
 * Background fill shared by [GroupedContainer] and individually-shaped grouped rows.
 */
@Composable
fun groupedSurfaceColor(): androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTAINER_FILL_ALPHA)

/**
 * Faint enough that the container reads as a grouping rather than a raised card, but still
 * clearly separated from the screen background in both light and dark themes.
 */
private const val CONTAINER_FILL_ALPHA = 0.40f
