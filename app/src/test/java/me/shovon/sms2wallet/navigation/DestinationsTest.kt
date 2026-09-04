package me.shovon.sms2wallet.navigation

import me.shovon.sms2wallet.domain.nlp.NlPrefill
import me.shovon.sms2wallet.presentation.navigation.Sms2WalletDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {

    @Test
    fun parserPlaygroundRouteWithoutArgsProducesCleanPath() {
        val route = Sms2WalletDestination.ParserPlayground.createRoute()
        assertEquals("settings/parser_playground", route)
        assertFalse(route.contains("{sender}"))
        assertFalse(route.contains("{body}"))
    }

    @Test
    fun parserPlaygroundRouteWithArgsIncludesParameters() {
        val route = Sms2WalletDestination.ParserPlayground.createRoute(
            sender = "bKash",
            body = "You have received Tk 500"
        )
        assertTrue(route.startsWith("settings/parser_playground?"))
        assertTrue(route.contains("sender=bKash"))
    }

    @Test
    fun addCashExpenseRouteWithoutPrefillProducesCleanPath() {
        val route = Sms2WalletDestination.AddCashExpense.createRoute()
        assertEquals("add_cash_expense", route)
        assertFalse(route.contains("{merchant}"))
        assertFalse(route.contains("{amount}"))
    }

    @Test
    fun addCashExpenseRouteWithPrefillFormatsCorrectly() {
        val prefill = NlPrefill(
            amountText = "150",
            isIncome = false,
            merchant = "Coffee Shop",
            note = "Espresso",
            categoryName = "Food",
            accountName = "Cash"
        )
        val route = Sms2WalletDestination.AddCashExpense.createRoute(prefill)
        assertTrue(route.startsWith("add_cash_expense?"))
        assertTrue(route.contains("amount=150"))
        assertTrue(route.contains("income=false"))
        // Navigation decodes with Uri.decode, which does not turn "+" back into a space. Form
        // encoding here would put "Coffee+Shop" in the merchant field on the add screen.
        assertTrue(route.contains("merchant=Coffee%20Shop"))
        assertFalse(route.contains("Coffee+Shop"))
    }

    @Test
    fun routeValuesSurviveCharactersThatWouldOtherwiseBreakTheQuery() {
        val route = Sms2WalletDestination.AddCashExpense.createRoute(
            NlPrefill(
                amountText = "1200",
                isIncome = false,
                merchant = "M&S / Food",
                note = "50% off + tip",
            )
        )

        // Raw "&", "/", "=" or "#" would split the query or truncate at a fragment.
        assertTrue(route.contains("merchant=M%26S%20%2F%20Food"))
        assertTrue(route.contains("note=50%25%20off%20%2B%20tip"))
    }

    @Test
    fun parserPlaygroundBodyKeepsItsSpaces() {
        val route = Sms2WalletDestination.ParserPlayground.createRoute(
            sender = "bKash",
            body = "You have received Tk 500"
        )

        assertTrue(route.contains("body=You%20have%20received%20Tk%20500"))
        assertFalse(route.contains("+"))
    }

    @Test
    fun anEmptyAmountIsLeftOutOfTheRouteEntirely() {
        val route = Sms2WalletDestination.AddCashExpense.createRoute(
            NlPrefill(amountText = "", isIncome = false, merchant = "Uber")
        )

        // Rather than "amount=", which the form would read back as a blank it has to validate.
        assertFalse(route.contains("amount="))
        assertTrue(route.contains("merchant=Uber"))
    }

    @Test
    fun transactionDetailRouteFormatsWithTransactionId() {
        val route = Sms2WalletDestination.TransactionDetail.createRoute("42")
        assertEquals("review_queue/transaction/42", route)
    }
}
