package me.shovon.sms2wallet.presentation.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.sms2wallet.presentation.components.AppTextField
import me.shovon.sms2wallet.presentation.components.BadgeIntent
import me.shovon.sms2wallet.presentation.components.GroupedContainer
import me.shovon.sms2wallet.presentation.components.SectionDivider
import me.shovon.sms2wallet.presentation.components.SectionHeader
import me.shovon.sms2wallet.presentation.components.Sms2WalletScaffold
import me.shovon.sms2wallet.presentation.components.StatusBadge
import me.shovon.sms2wallet.presentation.components.groupedSurfaceColor
import me.shovon.sms2wallet.presentation.model.DashboardUiState
import me.shovon.sms2wallet.presentation.model.QuickAddUiState
import me.shovon.sms2wallet.presentation.model.RateLimitUiState
import me.shovon.sms2wallet.presentation.model.SampleData
import me.shovon.sms2wallet.presentation.model.TokenHealth
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.MinTouchTarget
import me.shovon.sms2wallet.presentation.theme.MotionDuration
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.theme.StandardEasing

/**
 * Home tab: today/this-week push counters, pending review count, quick actions,
 * last sync time, token health, and the Wallet API rate-limit budget.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAddCashExpense: () -> Unit,
    onViewReviewQueue: () -> Unit,
    onOpenParserPlayground: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenUnmatchedSms: () -> Unit = {},
    onQuickAddInputChange: (String) -> Unit = {},
    onQuickAddSubmit: () -> Unit = {}
) {
    Sms2WalletScaffold(
        title = "Dashboard"
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl
            )
        ) {
            // Financial Hub Hero Card
            item(key = "hero") {
                DashboardHeroCard(
                    state = state,
                    onAddCashExpense = onAddCashExpense,
                    onViewReviewQueue = onViewReviewQueue,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
            }

            // Stat counters with icon badges
            item(key = "counters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Pushed today",
                        value = state.pushedToday.toString(),
                        badgeText = "Today",
                        icon = SolarIcons.CheckCircle,
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "This week",
                        value = state.pushedThisWeek.toString(),
                        badgeText = "7 days",
                        icon = SolarIcons.PublishedWithChanges,
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Pending review CTA card
            item(key = "pending") {
                PendingReviewCard(
                    pendingCount = state.pendingReviewCount,
                    onClick = onViewReviewQueue,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }

            // Natural language quick add (powered by Gemini)
            if (state.quickAdd.isAvailable) {
                item(key = "quick-add") {
                    QuickAddCard(
                        state = state.quickAdd,
                        onInputChange = onQuickAddInputChange,
                        onSubmit = onQuickAddSubmit,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                }
            }

            // Quick Tools Grid (3-column dedicated cards)
            item(key = "quick-tools-header") {
                SectionHeader(
                    title = "Tools",
                    supportingText = "Testing, logs and unmatched SMS",
                    modifier = Modifier.padding(top = Spacing.xl)
                )
            }

            item(key = "quick-tools") {
                QuickToolsGrid(
                    onOpenPlayground = onOpenParserPlayground,
                    onOpenActivity = onOpenActivity,
                    onOpenUnmatched = onOpenUnmatchedSms
                )
            }

            // Pipeline Status section
            item(key = "status-header") {
                SectionHeader(
                    title = "Integration & Health",
                    supportingText = "Live health of your Wallet API connection",
                    modifier = Modifier.padding(top = Spacing.xl)
                )
            }

            item(key = "status-body") {
                GroupedContainer {
                    InfoRow(
                        icon = SolarIcons.Sync,
                        title = "Last sync",
                        value = state.lastSyncLabel ?: "Never synced yet"
                    )
                    SectionDivider(startInset = STATUS_DIVIDER_INSET)
                    TokenHealthRow(tokenHealth = state.tokenHealth)
                    SectionDivider(startInset = STATUS_DIVIDER_INSET)
                    RateLimitRow(rateLimit = state.rateLimit)
                }
            }
        }
    }
}

private val STATUS_DIVIDER_INSET = 56.dp

/**
 * Top command-center card showing integration status and rapid shortcuts.
 */
@Composable
private fun DashboardHeroCard(
    state: DashboardUiState,
    onAddCashExpense: () -> Unit,
    onViewReviewQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = groupedSurfaceColor()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = SolarIcons.Wallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.md)
                        )
                    }
                    Column {
                        Text(
                            text = "Sync Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.lastSyncLabel?.let { "Updated $it" } ?: "Not synced yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isConnected = state.tokenHealth == TokenHealth.VALID
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isConnected) {
                        Sms2WalletTheme.extendedColors.income.copy(alpha = 0.15f)
                    } else {
                        Sms2WalletTheme.extendedColors.warningContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isConnected) Sms2WalletTheme.extendedColors.income
                                    else Sms2WalletTheme.extendedColors.warning
                                )
                        )
                        Text(
                            text = if (isConnected) "Active" else "Check Token",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isConnected) Sms2WalletTheme.extendedColors.income
                            else Sms2WalletTheme.extendedColors.onWarningContainer
                        )
                    }
                }
            }

            // Quick actions inside hero card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (state.pendingReviewCount > 0) {
                    FilledTonalButton(
                        onClick = onViewReviewQueue,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = SolarIcons.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.sm)
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text("Review (${state.pendingReviewCount})")
                    }
                }
                FilledTonalButton(
                    onClick = onAddCashExpense,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = SolarIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Add Cash")
                }
            }
        }
    }
}

/**
 * Enhanced StatCard with leading soft circular container and distinct visual rhythm.
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value ($badgeText)"
        },
        shape = MaterialTheme.shapes.large,
        color = groupedSurfaceColor()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(IconSize.md)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.xs)
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive card highlighting pending transactions with forward chevron and rich feedback.
 */
@Composable
private fun PendingReviewCard(
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPending = pendingCount > 0
    val accessibilityLabel = if (hasPending) {
        "$pendingCount transactions pending review. Tap to view review queue."
    } else {
        "Review queue is all caught up. No transactions pending."
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibilityLabel
            },
        shape = MaterialTheme.shapes.large,
        color = if (hasPending) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            groupedSurfaceColor()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasPending) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            Sms2WalletTheme.extendedColors.income.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasPending) SolarIcons.Checklist else SolarIcons.Check,
                    contentDescription = null,
                    tint = if (hasPending) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        Sms2WalletTheme.extendedColors.income
                    },
                    modifier = Modifier.size(IconSize.lg)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPending) "Pending review" else "All caught up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasPending) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = when (pendingCount) {
                        0 -> "No SMS transactions waiting"
                        1 -> "1 transaction ready to check & push"
                        else -> "$pendingCount transactions ready to check & push"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasPending) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            }

            if (hasPending) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = pendingCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = SolarIcons.CaretRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
            } else {
                Icon(
                    imageVector = SolarIcons.CaretRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.md)
                )
            }
        }
    }
}

/**
 * Modern 3-column tools grid for fast one-tap shortcuts.
 */
@Composable
private fun QuickToolsGrid(
    onOpenPlayground: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenUnmatched: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        QuickToolCard(
            modifier = Modifier.weight(1f),
            icon = SolarIcons.Science,
            title = "Playground",
            subtitle = "Test regex",
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenPlayground
        )
        QuickToolCard(
            modifier = Modifier.weight(1f),
            icon = SolarIcons.History,
            title = "Activity",
            subtitle = "Push logs",
            iconColor = MaterialTheme.colorScheme.tertiary,
            onClick = onOpenActivity
        )
        QuickToolCard(
            modifier = Modifier.weight(1f),
            icon = SolarIcons.MarkEmailUnread,
            title = "Unmatched",
            subtitle = "Raw texts",
            iconColor = Sms2WalletTheme.extendedColors.warning,
            onClick = onOpenUnmatched
        )
    }
}

@Composable
private fun QuickToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 96.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title, $subtitle"
            },
        shape = MaterialTheme.shapes.large,
        color = groupedSurfaceColor()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(IconSize.md)
                )
            }
            Column(modifier = Modifier.padding(top = Spacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TokenHealthRow(tokenHealth: TokenHealth) {
    val (icon: ImageVector, label: String, intent: BadgeIntent) = when (tokenHealth) {
        TokenHealth.VALID -> Triple(SolarIcons.CheckCircle, "Valid", BadgeIntent.INFO)
        TokenHealth.EXPIRING_SOON -> Triple(SolarIcons.HourglassTop, "Expires soon", BadgeIntent.WARNING)
        TokenHealth.SYNCING -> Triple(SolarIcons.Sync, "Syncing", BadgeIntent.INFO)
        TokenHealth.INVALID -> Triple(SolarIcons.Error, "Invalid", BadgeIntent.WARNING)
        TokenHealth.UNKNOWN -> Triple(SolarIcons.Error, "Unknown", BadgeIntent.NEUTRAL)
    }

    val healthDesc = when (tokenHealth) {
        TokenHealth.VALID -> "Wallet API token is active"
        TokenHealth.EXPIRING_SOON -> "Token will expire soon"
        TokenHealth.SYNCING -> "Wallet is building initial sync"
        TokenHealth.INVALID -> "Token was rejected by Wallet"
        TokenHealth.UNKNOWN -> "Status not checked yet"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = "Token health: $label, $healthDesc"
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Token health",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = healthDesc,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
        }
        StatusBadge(text = label, intent = intent)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: $value"
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.lg)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
        }
    }
}

@Composable
private fun RateLimitRow(rateLimit: RateLimitUiState) {
    val remaining = (rateLimit.limit - rateLimit.used).coerceAtLeast(0)
    val fraction = rateLimit.fraction
    val meterColor = when {
        fraction > 0.85f -> MaterialTheme.colorScheme.error
        fraction > 0.65f -> Sms2WalletTheme.extendedColors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Wallet API budget: ${rateLimit.used} of ${rateLimit.limit} used, $remaining remaining"
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = SolarIcons.Speed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "API budget",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${rateLimit.used} / ${rateLimit.limit} (${remaining} left)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                color = meterColor,
                trackColor = meterColor.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .height(PROGRESS_HEIGHT),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

private val PROGRESS_HEIGHT = 8.dp

private val QUICK_ADD_SUGGESTIONS = listOf(
    "taxi 240",
    "dinner 650",
    "groceries 1200",
    "coffee 180",
    "electricity 2200"
)

/**
 * Natural-language entry card with interactive suggestion chips and Gemini indicator.
 */
@Composable
private fun QuickAddCard(
    state: QuickAddUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        if (!state.canSubmit) return
        keyboardController?.hide()
        onSubmit()
    }

    GroupedContainer(modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = SolarIcons.Sparkle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.sm)
                )
                Text(
                    text = "Quick add with Gemini",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AppTextField(
                label = "Type transaction",
                value = state.input,
                onValueChange = onInputChange,
                placeholder = "e.g. uber 120 or grocery 850",
                supportingText = if (state.errorMessage == null) {
                    "Parsed on-device with Gemini. You confirm before saving."
                } else {
                    null
                },
                errorText = state.errorMessage,
                enabled = !state.isParsing,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    if (state.isParsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.md),
                            strokeWidth = 2.dp
                        )
                    } else {
                        FilledIconButton(
                            onClick = ::submit,
                            enabled = state.canSubmit
                        ) {
                            Icon(
                                imageVector = SolarIcons.Add,
                                contentDescription = "Parse and open add screen"
                            )
                        }
                    }
                }
            )

            // Interactive suggestion chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                QUICK_ADD_SUGGESTIONS.forEach { suggestion ->
                    SuggestionPill(
                        text = suggestion,
                        onClick = { onInputChange(suggestion) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 36.dp)
            .semantics {
                contentDescription = "Suggestion: $text"
            },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Dashboard - Light", showBackground = true)
@Composable
private fun DashboardScreenLightPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.BRAND) {
        DashboardScreen(
            state = SampleData.dashboard,
            onAddCashExpense = {},
            onViewReviewQueue = {}
        )
    }
}

@Preview(name = "Dashboard - Dark", showBackground = true)
@Composable
private fun DashboardScreenDarkPreview() {
    Sms2WalletTheme(themeMode = ThemeMode.DARK, accentColor = AccentColor.BRAND) {
        DashboardScreen(
            state = SampleData.dashboard,
            onAddCashExpense = {},
            onViewReviewQueue = {}
        )
    }
}
