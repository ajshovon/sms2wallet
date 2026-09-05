package me.shovon.sms2wallet.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Spacing
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.shovon.sms2wallet.presentation.screens.activity.ActivityContent
import me.shovon.sms2wallet.presentation.screens.activity.ActivityViewModel
import me.shovon.sms2wallet.presentation.screens.activity.UnmatchedSmsContent
import me.shovon.sms2wallet.presentation.screens.activity.UnmatchedSmsViewModel
import me.shovon.sms2wallet.presentation.screens.addexpense.AddCashExpenseScreen
import me.shovon.sms2wallet.presentation.screens.dashboard.DashboardScreen
import me.shovon.sms2wallet.presentation.screens.dashboard.DashboardViewModel
import me.shovon.sms2wallet.presentation.screens.playground.ParserPlaygroundScreen
import me.shovon.sms2wallet.presentation.screens.review.ReviewQueueContent
import me.shovon.sms2wallet.presentation.screens.review.ReviewQueueViewModel
import me.shovon.sms2wallet.presentation.screens.review.TransactionDetailScreen
import me.shovon.sms2wallet.presentation.screens.settings.SettingsContent
import me.shovon.sms2wallet.presentation.screens.settings.SettingsViewModel

/**
 * App root: a bottom-navigation [Scaffold] hosting the four top-level tabs, plus a [NavHost]
 * for detail routes pushed on top of them (transaction detail, add-cash-expense, parser
 * playground, unmatched SMS).
 */
@Composable
fun Sms2WalletRootScreen(
    navController: NavHostController = rememberNavController(),
    openTransactionId: Long? = null,
    onTransactionOpened: () -> Unit = {},
) {
    // Navigate straight to the transaction a notification was tapped for. Keyed on the id so it
    // fires once per notification and not again on every recomposition.
    LaunchedEffect(openTransactionId) {
        val id = openTransactionId ?: return@LaunchedEffect
        navController.navigate(Sms2WalletDestination.TransactionDetail.createRoute(id.toString()))
        onTransactionOpened()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.destination.route } == true
    }

    val rootNavViewModel: RootNavViewModel = hiltViewModel()
    val pendingReviewCount by rootNavViewModel.pendingReviewCount.collectAsStateWithLifecycle()

    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = 64.dp + navBarBottomInset

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Sms2WalletDestination.Dashboard.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Sms2WalletDestination.Dashboard.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomBarHeight)) {
                    DashboardScreen(
                        state = state,
                        onAddCashExpense = {
                            navController.navigate(Sms2WalletDestination.AddCashExpense.createRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onViewReviewQueue = { navigateToTab(Sms2WalletDestination.ReviewQueue.route) },
                        onOpenParserPlayground = { navController.navigate(Sms2WalletDestination.ParserPlayground.createRoute()) },
                        onOpenActivity = { navigateToTab(Sms2WalletDestination.Activity.route) },
                        onOpenUnmatchedSms = { navController.navigate(Sms2WalletDestination.UnmatchedSms.route) },
                        onQuickAddInputChange = viewModel::onQuickAddInputChange,
                        onQuickAddSubmit = {
                            viewModel.submitQuickAdd { prefill ->
                                navController.navigate(
                                    Sms2WalletDestination.AddCashExpense.createRoute(prefill)
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
            composable(Sms2WalletDestination.ReviewQueue.route) {
                val viewModel: ReviewQueueViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                // Confirms bulk actions ("Dismissed 12 transactions"), which are otherwise
                // silent: the rows simply vanish, and the user has no way to tell a successful
                // dismiss-all from a crash.
                LaunchedEffect(viewModel) {
                    viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
                }

                Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomBarHeight)) {
                    ReviewQueueContent(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onOpenTransaction = { id ->
                            navController.navigate(Sms2WalletDestination.TransactionDetail.createRoute(id))
                        },
                        onPush = viewModel::approve,
                        onDismiss = viewModel::dismiss,
                        onToggleMultiSelect = viewModel::toggleMultiSelect,
                        onToggleSelected = viewModel::setSelected,
                        onBulkPush = viewModel::approveSelected,
                        onBulkDismiss = viewModel::dismissSelected,
                        onDismissAll = viewModel::dismissAll,
                    onSuggestCategories = viewModel::suggestMissingCategories
                    )
                }
            }
            composable(Sms2WalletDestination.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomBarHeight)) {
                    SettingsContent(
                        state = state,
                        onOpenParserPlayground = { navController.navigate(Sms2WalletDestination.ParserPlayground.createRoute()) },
                        onTokenChange = viewModel::onTokenChange,
                        onToggleTokenVisibility = viewModel::onToggleTokenVisibility,
                        onTestConnection = viewModel::testConnection,
                        onSyncWalletData = viewModel::syncWalletData,
                        onThemeModeChange = viewModel::setThemeMode,
                        onAccentColorChange = viewModel::setAccentColor,
                        onGeminiKeyChange = viewModel::onGeminiKeyChange,
                        onToggleGeminiKeyVisibility = viewModel::onToggleGeminiKeyVisibility,
                        onTestGeminiKey = viewModel::testGeminiKey,
                        onClearGeminiKey = viewModel::clearGeminiKey,
                        onGeminiModelChange = viewModel::setGeminiModel,
                        onShareCategoryNamesChange = viewModel::setShareCategoryNames,
                        onShareAccountNamesChange = viewModel::setShareAccountNames,
                    onShareMerchantNamesChange = viewModel::setShareMerchantNames,
                    onDeleteLearnedCategory = viewModel::deleteLearnedCategory,
                        onDefaultAccountChange = viewModel::setDefaultAccount,
                        onParserEnabledChange = viewModel::setParserEnabled,
                        onParserAutoPushChange = viewModel::setParserAutoPush,
                        onAccountMappingChange = viewModel::setAccountMapping,
                        onReminderEnabledChange = viewModel::setReminderEnabled,
                        onReminderTimeChange = viewModel::setReminderTime,
                        onReminderSkipCountChange = viewModel::setReminderSkipCount
                    )
                }
            }
            composable(Sms2WalletDestination.Activity.route) {
                val viewModel: ActivityViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomBarHeight)) {
                    ActivityContent(
                        state = state,
                        onOpenUnmatchedSms = { navController.navigate(Sms2WalletDestination.UnmatchedSms.route) },
                        onRetry = viewModel::retry
                    )
                }
            }
            composable(Sms2WalletDestination.UnmatchedSms.route) {
                val viewModel: UnmatchedSmsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                UnmatchedSmsContent(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onDismiss = viewModel::dismiss,
                    onTestInPlayground = { sender, body ->
                        navController.navigate(
                            Sms2WalletDestination.ParserPlayground.createRoute(sender, body)
                        )
                    }
                )
            }
            composable(
                route = Sms2WalletDestination.ParserPlayground.route,
                arguments = Sms2WalletDestination.PARSER_PLAYGROUND_ARGS.map { name ->
                    navArgument(name) {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                }
            ) {
                ParserPlaygroundScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Sms2WalletDestination.AddCashExpense.route,
                // Every argument is optional and defaults to empty, so the bare
                // "add_cash_expense" route from the FAB matches this same entry.
                arguments = Sms2WalletDestination.ADD_CASH_EXPENSE_ARGS.map { name ->
                    navArgument(name) {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                },
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(150))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(150))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            ) {
                AddCashExpenseScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.popBackStack()
                        navigateToTab(Sms2WalletDestination.Settings.route)
                    }
                )
            }
            composable(
                route = Sms2WalletDestination.TransactionDetail.route,
                arguments = listOf(navArgument(Sms2WalletDestination.ARG_TRANSACTION_ID) {
                    type = androidx.navigation.NavType.StringType
                })
            ) {
                // transactionId is read from SavedStateHandle inside the ViewModel rather than
                // being threaded through the composable.
                TransactionDetailScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }

        // Animated Bottom Bar with Right Cutout Cradle and Pop-out FAB
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(150)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Sms2WalletBottomBar(
                currentDestination = currentDestination,
                pendingReviewCount = pendingReviewCount,
                onNavigateToDestination = navigateToTab,
                onAddCashExpense = {
                    navController.navigate(Sms2WalletDestination.AddCashExpense.createRoute()) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

/**
 * Custom sculpted navigation bar shape featuring an asymmetrical, fluid cutout cradle on the
 * right flank that houses the floating action button.
 */
private class CutoutNotchShape(
    private val notchRadius: Dp,
    private val cornerRadius: Dp,
    private val notchMarginEnd: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(
                Rect(0f, 0f, size.width.coerceAtLeast(0f), size.height.coerceAtLeast(0f))
            )
        }

        val r = with(density) { notchRadius.toPx() }
        val cr = with(density) { cornerRadius.toPx() }
        val me = with(density) { notchMarginEnd.toPx() }

        val cx = size.width - me - r
        val notchHalfWidth = r * 1.35f
        val notchDepth = r * 0.70f
        val startX = (cx - notchHalfWidth).coerceAtLeast(cr)
        val endX = (cx + notchHalfWidth).coerceAtMost(size.width - cr)

        val path = Path().apply {
            moveTo(0f, cr)
            arcTo(
                rect = Rect(0f, 0f, 2 * cr, 2 * cr),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(startX, 0f)

            // Smooth C1-continuous cubic Bezier curve into cradle trough
            cubicTo(
                startX + notchHalfWidth * 0.35f, 0f,
                cx - notchHalfWidth * 0.45f, notchDepth,
                cx, notchDepth
            )
            // Smooth C1-continuous cubic Bezier curve back to top line
            cubicTo(
                cx + notchHalfWidth * 0.45f, notchDepth,
                endX - notchHalfWidth * 0.35f, 0f,
                endX, 0f
            )

            lineTo(size.width - cr, 0f)
            arcTo(
                rect = Rect(size.width - 2 * cr, 0f, size.width, 2 * cr),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        return Outline.Generic(path)
    }
}

/**
 * Asymmetrical fluid bottom navigation bar with a right-flanked cutout action cradle.
 *
 * Design features:
 * - Fluid Navigation Island: 4 destinations [Dashboard, Review, Activity, Settings] distributed
 *   on the left, with the active tab expanding into an IBKR-styled animated capsule pill and
 *   inactive tabs resting as sleek circular icon discs.
 * - Sculpted Cutout Cradle: A mathematically smooth cubic Bezier notch on the right edge.
 * - Dedicated Action Pod: Floating circular '+' button nestled inside the cradle with an
 *   elevated pop-out stance and surface ring border, making creation visually distinct from
 *   navigation destinations.
 */
@Composable
private fun Sms2WalletBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    pendingReviewCount: Int,
    onNavigateToDestination: (String) -> Unit,
    onAddCashExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = 64.dp + navBarBottomInset

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Sculpted Bar Surface with Right Cutout Cradle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutoutNotchShape(
                notchRadius = 30.dp,
                cornerRadius = 24.dp,
                notchMarginEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBarHeight)
                    .padding(bottom = navBarBottomInset)
                    .padding(start = Spacing.md, end = 86.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 0: Dashboard
                val dashboardSelected = currentDestination?.hierarchy?.any {
                    it.route == bottomNavItems[0].destination.route
                } == true
                IbkrNavTab(
                    item = bottomNavItems[0],
                    selected = dashboardSelected,
                    badgeCount = 0,
                    onClick = { onNavigateToDestination(bottomNavItems[0].destination.route) }
                )

                // Tab 1: Review Queue
                val reviewSelected = currentDestination?.hierarchy?.any {
                    it.route == bottomNavItems[1].destination.route
                } == true
                IbkrNavTab(
                    item = bottomNavItems[1],
                    selected = reviewSelected,
                    badgeCount = pendingReviewCount,
                    onClick = { onNavigateToDestination(bottomNavItems[1].destination.route) }
                )

                // Tab 2: Activity
                val activitySelected = currentDestination?.hierarchy?.any {
                    it.route == bottomNavItems[2].destination.route
                } == true
                IbkrNavTab(
                    item = bottomNavItems[2],
                    selected = activitySelected,
                    badgeCount = 0,
                    onClick = { onNavigateToDestination(bottomNavItems[2].destination.route) }
                )

                // Tab 3: Settings
                val settingsSelected = currentDestination?.hierarchy?.any {
                    it.route == bottomNavItems[3].destination.route
                } == true
                IbkrNavTab(
                    item = bottomNavItems[3],
                    selected = settingsSelected,
                    badgeCount = 0,
                    onClick = { onNavigateToDestination(bottomNavItems[3].destination.route) }
                )
            }
        }

        // Distinct Floating Action Pod Nestled in the Cutout Cradle (Right-Flanked)
        val fabInteractionSource = remember { MutableInteractionSource() }
        val isFabPressed by fabInteractionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (isFabPressed) 0.90f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "fab_scale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = navBarBottomInset + 18.dp
                )
        ) {
            Surface(
                onClick = onAddCashExpense,
                interactionSource = fabInteractionSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                border = BorderStroke(3.5.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .scale(fabScale)
                    .size(52.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Add cash expense"
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = SolarIcons.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual tab styled after the IBKR navigation bar:
 * - Active: Rounded pill container with a circular accent badge on the left and bold text.
 * - Inactive: Sleek circular button with subtle tinted icon.
 */
@Composable
private fun IbkrNavTab(
    item: BottomNavItem,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spokenLabel = if (badgeCount > 0) "${item.label}, $badgeCount pending" else item.label

    if (selected) {
        // Active Tab: Expanded Capsule Pill
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = modifier
                .height(48.dp)
                .semantics {
                    role = Role.Tab
                    this.selected = true
                    contentDescription = spokenLabel
                }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 5.dp, end = 14.dp)
            ) {
                // Vibrant circular icon container
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.selectedIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    } else {
        // Inactive Tab: Circular Icon Button
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
            border = BorderStroke(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            ),
            modifier = modifier
                .size(48.dp)
                .semantics {
                    role = Role.Tab
                    this.selected = false
                    contentDescription = spokenLabel
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (badgeCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                            }
                        }
                    ) {
                        Icon(
                            imageVector = item.unselectedIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = item.unselectedIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
