package me.shovon.sms2wallet.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import me.shovon.sms2wallet.presentation.theme.PhosphorIcons

/** All navigable routes in the app. */
sealed class Sms2WalletDestination(val route: String) {

    data object Dashboard : Sms2WalletDestination("dashboard")
    data object ReviewQueue : Sms2WalletDestination("review_queue")
    data object Settings : Sms2WalletDestination("settings")
    data object Activity : Sms2WalletDestination("activity")

    data object UnmatchedSms : Sms2WalletDestination("activity/unmatched_sms")
    data object ParserPlayground : Sms2WalletDestination("settings/parser_playground")
    data object AddCashExpense : Sms2WalletDestination("add_cash_expense")

    data object TransactionDetail : Sms2WalletDestination("review_queue/transaction/{$ARG_TRANSACTION_ID}") {
        fun createRoute(transactionId: String) = "review_queue/transaction/$transactionId"
    }

    companion object {
        const val ARG_TRANSACTION_ID = "transactionId"
    }
}

/** One entry in the bottom navigation bar. */
data class BottomNavItem(
    val destination: Sms2WalletDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        destination = Sms2WalletDestination.Dashboard,
        label = "Dashboard",
        selectedIcon = PhosphorIcons.Home,
        unselectedIcon = PhosphorIcons.Home
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.ReviewQueue,
        label = "Review",
        selectedIcon = PhosphorIcons.Checklist,
        unselectedIcon = PhosphorIcons.Checklist
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.Settings,
        label = "Settings",
        selectedIcon = PhosphorIcons.Settings,
        unselectedIcon = PhosphorIcons.Settings
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.Activity,
        label = "Activity",
        selectedIcon = PhosphorIcons.History,
        unselectedIcon = PhosphorIcons.History
    )
)
