package me.shovon.sms2wallet.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.ReviewQueue,
        label = "Review",
        selectedIcon = Icons.Filled.Checklist,
        unselectedIcon = Icons.Outlined.Checklist
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.Settings,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.Activity,
        label = "Activity",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    )
)
