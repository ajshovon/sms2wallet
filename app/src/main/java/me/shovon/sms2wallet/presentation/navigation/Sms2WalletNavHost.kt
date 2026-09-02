package me.shovon.sms2wallet.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
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
fun Sms2WalletRootScreen(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Sms2WalletDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Sms2WalletDestination.Dashboard.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = state,
                    onAddCashExpense = { navController.navigate(Sms2WalletDestination.AddCashExpense.route) },
                    onViewReviewQueue = { navController.navigate(Sms2WalletDestination.ReviewQueue.route) }
                )
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
                    onDismissAll = viewModel::dismissAll
                )
            }
            composable(Sms2WalletDestination.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsContent(
                    state = state,
                    onOpenParserPlayground = { navController.navigate(Sms2WalletDestination.ParserPlayground.route) },
                    onTokenChange = viewModel::onTokenChange,
                    onToggleTokenVisibility = viewModel::onToggleTokenVisibility,
                    onTestConnection = viewModel::testConnection,
                    onSyncWalletData = viewModel::syncWalletData,
                    onParserEnabledChange = viewModel::setParserEnabled,
                    onParserAutoPushChange = viewModel::setParserAutoPush,
                    onAccountMappingChange = viewModel::setAccountMapping,
                    onReminderEnabledChange = viewModel::setReminderEnabled,
                    onReminderTimeChange = viewModel::setReminderTime,
                    onReminderSkipCountChange = viewModel::setReminderSkipCount
                )
            }
            composable(Sms2WalletDestination.Activity.route) {
                val viewModel: ActivityViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ActivityContent(
                    state = state,
                    onOpenUnmatchedSms = { navController.navigate(Sms2WalletDestination.UnmatchedSms.route) },
                    onRetry = viewModel::retry
                )
            }
            composable(Sms2WalletDestination.UnmatchedSms.route) {
                val viewModel: UnmatchedSmsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                UnmatchedSmsContent(state = state, onBack = { navController.popBackStack() })
            }
            composable(Sms2WalletDestination.ParserPlayground.route) {
                ParserPlaygroundScreen(onBack = { navController.popBackStack() })
            }
            composable(Sms2WalletDestination.AddCashExpense.route) {
                AddCashExpenseScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
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
    }
}
