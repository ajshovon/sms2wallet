package me.shovon.sms2wallet.presentation.navigation

import java.net.URLEncoder
import androidx.compose.ui.graphics.vector.ImageVector
import me.shovon.sms2wallet.domain.nlp.NlPrefill
import me.shovon.sms2wallet.presentation.theme.SolarIcons

/**
 * Percent-encodes one query-argument value for a navigation route.
 *
 * [URLEncoder] targets HTML form encoding, where a space becomes `+`. Navigation decodes route
 * arguments with `Uri.decode`, which treats `+` as a literal plus - so a form-encoded value
 * arrives with `+` where its spaces were ("Coffee+Shop"). Converting to `%20` is what makes the
 * two halves agree. A literal `+` in the input is already `%2B` by this point, so it survives.
 */
private fun encodeParam(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** All navigable routes in the app. */
sealed class Sms2WalletDestination(val route: String) {

    data object Dashboard : Sms2WalletDestination("dashboard")
    data object ReviewQueue : Sms2WalletDestination("review_queue")
    data object Settings : Sms2WalletDestination("settings")
    data object Activity : Sms2WalletDestination("activity")

    data object UnmatchedSms : Sms2WalletDestination("activity/unmatched_sms")
    data object ParserPlayground : Sms2WalletDestination(
        "settings/parser_playground?" +
            "$ARG_SENDER={$ARG_SENDER}" +
            "&$ARG_BODY={$ARG_BODY}"
    ) {
        fun createRoute(sender: String? = null, body: String? = null): String {
            if (sender == null && body == null) return "settings/parser_playground"
            val query = listOfNotNull(
                sender?.takeIf { it.isNotBlank() }?.let { "$ARG_SENDER=${encodeParam(it)}" },
                body?.takeIf { it.isNotBlank() }?.let { "$ARG_BODY=${encodeParam(it)}" },
            )
            return if (query.isEmpty()) "settings/parser_playground" else "settings/parser_playground?" + query.joinToString("&")
        }
    }
    /**
     * The manual add screen, optionally pre-filled from a typed phrase.
     *
     * The prefill travels as query arguments rather than through a shared holder so it survives
     * process death and rotation the same way the rest of the back stack does - a parse the user
     * paid an API call for should not evaporate because the screen rotated.
     */
    data object AddCashExpense : Sms2WalletDestination(
        "add_cash_expense?" +
            "$ARG_MERCHANT={$ARG_MERCHANT}" +
            "&$ARG_AMOUNT={$ARG_AMOUNT}" +
            "&$ARG_INCOME={$ARG_INCOME}" +
            "&$ARG_CATEGORY={$ARG_CATEGORY}" +
            "&$ARG_ACCOUNT={$ARG_ACCOUNT}" +
            "&$ARG_NOTE={$ARG_NOTE}"
    ) {
        /** With no [prefill] this is the plain route, which the optional arguments still match. */
        fun createRoute(prefill: NlPrefill? = null): String {
            if (prefill == null) return "add_cash_expense"
            val query = listOfNotNull(
                prefill.merchant.takeIf { it.isNotBlank() }?.let { "$ARG_MERCHANT=${encodeParam(it)}" },
                prefill.amountText.takeIf { it.isNotBlank() }?.let { "$ARG_AMOUNT=${encodeParam(it)}" },
                "$ARG_INCOME=${prefill.isIncome}",
                prefill.categoryName?.let { "$ARG_CATEGORY=${encodeParam(it)}" },
                prefill.accountName?.let { "$ARG_ACCOUNT=${encodeParam(it)}" },
                prefill.note?.let { "$ARG_NOTE=${encodeParam(it)}" },
            )
            return "add_cash_expense?" + query.joinToString("&")
        }
    }

    data object TransactionDetail : Sms2WalletDestination("review_queue/transaction/{$ARG_TRANSACTION_ID}") {
        fun createRoute(transactionId: String) = "review_queue/transaction/$transactionId"
    }

    companion object {
        const val ARG_TRANSACTION_ID = "transactionId"
        const val ARG_MERCHANT = "merchant"
        const val ARG_AMOUNT = "amount"
        const val ARG_INCOME = "income"
        const val ARG_CATEGORY = "category"
        const val ARG_ACCOUNT = "account"
        const val ARG_NOTE = "note"

        const val ARG_SENDER = "sender"
        const val ARG_BODY = "body"

        /** Every optional argument on [AddCashExpense], for declaring them in the nav graph. */
        val ADD_CASH_EXPENSE_ARGS = listOf(
            ARG_MERCHANT, ARG_AMOUNT, ARG_INCOME, ARG_CATEGORY, ARG_ACCOUNT, ARG_NOTE
        )

        /** Every optional argument on [ParserPlayground], for declaring them in the nav graph. */
        val PARSER_PLAYGROUND_ARGS = listOf(ARG_SENDER, ARG_BODY)
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
        selectedIcon = SolarIcons.Wallet,
        unselectedIcon = SolarIcons.Wallet
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.ReviewQueue,
        label = "Review",
        selectedIcon = SolarIcons.Receipt,
        unselectedIcon = SolarIcons.Receipt
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.Activity,
        label = "Activity",
        selectedIcon = SolarIcons.ArrowsLeftRight,
        unselectedIcon = SolarIcons.ArrowsLeftRight
    ),
    BottomNavItem(
        destination = Sms2WalletDestination.Settings,
        label = "Settings",
        selectedIcon = SolarIcons.SlidersHorizontal,
        unselectedIcon = SolarIcons.SlidersHorizontal
    )
)
