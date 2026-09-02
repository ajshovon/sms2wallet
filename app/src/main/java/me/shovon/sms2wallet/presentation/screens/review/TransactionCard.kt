package me.shovon.sms2wallet.presentation.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.MoneyText
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.model.ReviewTransactionUiState

/**
 * One Review-queue row. Swipe right to push, swipe left to dismiss; tap opens the detail
 * sheet. In multi-select mode swipes are disabled and a checkbox is shown instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionCard(
    transaction: ReviewTransactionUiState,
    onClick: () -> Unit,
    onPush: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}
) {
    if (isMultiSelectMode) {
        TransactionCardContent(
            transaction = transaction,
            modifier = modifier.fillMaxWidth(),
            onClick = { onSelectedChange(!isSelected) },
            leading = {
                Checkbox(checked = isSelected, onCheckedChange = onSelectedChange)
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

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            SwipeToDismissBoxValue.StartToEnd -> onPush()
            SwipeToDismissBoxValue.EndToStart -> onDismiss()
            else -> Unit
        }
        if (pendingAction != null) {
            dismissState.reset()
            pendingAction = null
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) }
    ) {
        TransactionCardContent(
            transaction = transaction,
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color: Color, icon, alignment, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> SwipeBackgroundSpec(
            color = MaterialTheme.colorScheme.primaryContainer,
            icon = Icons.Filled.CheckCircle,
            alignment = Alignment.CenterStart,
            label = "Push"
        )
        SwipeToDismissBoxValue.EndToStart -> SwipeBackgroundSpec(
            color = MaterialTheme.colorScheme.errorContainer,
            icon = Icons.Filled.Cancel,
            alignment = Alignment.CenterEnd,
            label = "Dismiss"
        )
        SwipeToDismissBoxValue.Settled -> SwipeBackgroundSpec(
            color = MaterialTheme.colorScheme.surfaceVariant,
            icon = null,
            alignment = Alignment.Center,
            label = ""
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .background(color, shape = MaterialTheme.shapes.medium),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let { Icon(it, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface) }
            if (label.isNotEmpty()) Text(label)
        }
    }
}

private data class SwipeBackgroundSpec(
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val alignment: Alignment,
    val label: String
)

@Composable
private fun TransactionCardContent(
    transaction: ReviewTransactionUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                it()
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = transaction.merchant, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${transaction.providerName} •••• ${transaction.accountLast4} • ${transaction.timeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.isSuspectedDuplicate || transaction.needsVerification) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (transaction.isSuspectedDuplicate) {
                            StatusBadge(text = "Suspected duplicate", intent = BadgeIntent.WARNING)
                        }
                        if (transaction.needsVerification) {
                            StatusBadge(text = "Needs verification", intent = BadgeIntent.INFO)
                        }
                    }
                }
            }
            MoneyText(amount = transaction.amount, direction = transaction.direction, style = MaterialTheme.typography.titleMedium)
        }
    }
}
