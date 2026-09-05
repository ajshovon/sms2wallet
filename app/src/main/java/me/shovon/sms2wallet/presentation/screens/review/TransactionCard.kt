package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.MoneyText
import me.shovon.sms2wallet.presentation.components.ProviderAvatar
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedRowShape
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.ReviewTransactionUiState
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MinTouchTarget
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.SolarIcons

/**
 * One Review-queue row, rendered as part of a grouped day list rather than as its own card.
 *
 * Each row previously sat in a standalone `Card`, which gave a screen of unrelated islands and
 * one shadow per transaction. Here the day group is the container: [index]/[count] round only
 * the group's outer corners, so the rows read as a single list and the eye can scan the amount
 * column without crossing an edge on every line.
 *
 * Swipe right pushes, swipe left dismisses; in multi-select mode swipes are off and a checkbox
 * takes the leading slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionCard(
    transaction: ReviewTransactionUiState,
    onClick: () -> Unit,
    onPush: () -> Unit,
    onDismiss: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}
) {
    val shape = groupedRowShape(index = index, count = count)

    if (isMultiSelectMode) {
        TransactionRowContent(
            transaction = transaction,
            shape = shape,
            modifier = modifier.fillMaxWidth(),
            onClick = { onSelectedChange(!isSelected) },
            isSelected = isSelected,
            leading = {
                Checkbox(checked = isSelected, onCheckedChange = null)
            }
        )
        return
    }

    // confirmValueChange can fire more than once for a single gesture, and it fires while the
    // drag is still settling. Acting directly inside it therefore risked invoking onPush twice
    // for one swipe - which, for an app whose whole promise is "never creates duplicates", is
    // exactly the wrong bug. Instead the callback only records the direction, and a
    // LaunchedEffect keyed on that direction runs the action exactly once per settled swipe.
    var pendingAction by remember(transaction.id) { mutableStateOf<SwipeToDismissBoxValue?>(null) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && pendingAction == null) {
                pendingAction = value
            }
            // Never let the box actually dismiss: the row leaves the list only when the
            // database says so, so an action that fails leaves the card visibly in place.
            false
        }
    )

    val haptics = LocalHapticFeedback.current

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            // The row does not leave until the database confirms it, so without this the gesture
            // has no immediate feedback at all and reads as "nothing happened".
            SwipeToDismissBoxValue.StartToEnd -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPush()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
            }
            else -> Unit
        }
        if (pendingAction != null) {
            dismissState.reset()
            pendingAction = null
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Push to Wallet") {
                        onPush()
                        true
                    },
                    CustomAccessibilityAction("Dismiss transaction") {
                        onDismiss()
                        true
                    }
                )
            },
        backgroundContent = { SwipeBackground(dismissState.dismissDirection, shape) }
    ) {
        TransactionRowContent(
            transaction = transaction,
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    direction: SwipeToDismissBoxValue,
    shape: androidx.compose.ui.graphics.Shape
) {
    val spec = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> SwipeBackgroundSpec(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = SolarIcons.CheckCircle,
            alignment = Alignment.CenterStart,
            label = "Push"
        )
        SwipeToDismissBoxValue.EndToStart -> SwipeBackgroundSpec(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = SolarIcons.Cancel,
            alignment = Alignment.CenterEnd,
            label = "Dismiss"
        )
        SwipeToDismissBoxValue.Settled -> SwipeBackgroundSpec(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = null,
            alignment = Alignment.Center,
            label = ""
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(spec.color),
        contentAlignment = spec.alignment
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            spec.icon?.let { icon ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(spec.contentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = spec.contentColor,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
            }
            if (spec.label.isNotEmpty()) {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = spec.contentColor
                )
            }
        }
    }
}

private data class SwipeBackgroundSpec(
    val color: Color,
    val contentColor: Color,
    val icon: ImageVector?,
    val alignment: Alignment,
    val label: String
)

@Composable
private fun TransactionRowContent(
    transaction: ReviewTransactionUiState,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    leading: @Composable (() -> Unit)? = null
) {
    val rowDesc = buildString {
        append("${transaction.merchant}, ${transaction.amount} Taka, ${transaction.sourceSummary}")
        if (transaction.category != null) append(", ${transaction.category}")
        if (transaction.accountName != null) append(", to ${transaction.accountName}")
        if (transaction.needsAttention) append(", needs attention")
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else groupedSurfaceColor()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable(
                    role = if (leading != null) Role.Checkbox else Role.Button,
                    onClick = onClick
                )
                .semantics {
                    contentDescription = rowDesc
                    if (leading != null) {
                        selected = isSelected
                    }
                }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (leading != null) {
                leading()
            } else {
                ProviderAvatar(
                    providerName = transaction.providerName,
                    direction = transaction.direction,
                    size = 42.dp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = transaction.sourceSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )

                // Metadata tags: Category, Account destination, or Warnings
                val hasTags = transaction.category != null || transaction.accountName != null || transaction.needsAttention
                if (hasTags) {
                    Row(
                        modifier = Modifier.padding(top = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        transaction.category?.let { cat ->
                            StatusBadge(text = cat, intent = BadgeIntent.NEUTRAL)
                        }

                        if (transaction.accountName != null) {
                            StatusBadge(text = transaction.accountName, intent = BadgeIntent.INFO)
                        } else {
                            StatusBadge(text = "Unmapped", intent = BadgeIntent.WARNING)
                        }

                        if (transaction.isSuspectedDuplicate) {
                            StatusBadge(text = "Duplicate?", intent = BadgeIntent.WARNING)
                        }
                        if (transaction.needsVerification) {
                            StatusBadge(text = "Verify", intent = BadgeIntent.INFO)
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                MoneyText(
                    amount = transaction.amount,
                    direction = transaction.direction,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Icon(
                    imageVector = SolarIcons.CaretRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(IconSize.sm)
                )
            }
        }
    }
}
