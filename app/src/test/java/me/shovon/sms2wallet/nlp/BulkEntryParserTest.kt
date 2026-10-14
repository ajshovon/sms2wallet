package me.shovon.sms2wallet.nlp

import java.math.BigDecimal
import me.shovon.sms2wallet.domain.nlp.BulkEntryParser
import me.shovon.sms2wallet.domain.nlp.BulkEntryParser.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkEntryParserTest {

    private fun expense(merchant: String, amount: String) = Entry(merchant, BigDecimal(amount), isIncome = false)

    @Test
    fun `a comma separated day becomes one entry per item, repeats kept`() {
        val entries = BulkEntryParser.parse("uber 120, tea 20, cig 10, uber 130, snacks 30")

        assertEquals(
            listOf(
                expense("Uber", "120"),
                expense("Tea", "20"),
                expense("Cig", "10"),
                expense("Uber", "130"),
                expense("Snacks", "30"),
            ),
            entries,
        )
    }

    @Test
    fun `new lines and semicolons separate items too`() {
        val entries = BulkEntryParser.parse("uber 120\ntea 20; snacks 30\r\n")

        assertEquals(listOf("Uber", "Tea", "Snacks"), entries.map { it.merchant })
    }

    @Test
    fun `a comma between digits is a thousands separator, in either grouping`() {
        val entries = BulkEntryParser.parse("groceries 1,200, rent 1,20,000, tea 20")

        assertEquals(listOf(BigDecimal("1200"), BigDecimal("120000"), BigDecimal("20")), entries.map { it.amount })
    }

    @Test
    fun `a plus marks income wherever it sits`() {
        val entries = BulkEntryParser.parse("+500 salary, refund +200, tea 20")

        assertEquals(listOf(true, true, false), entries.map { it.isIncome })
        assertEquals(listOf("Salary", "Refund", "Tea"), entries.map { it.merchant })
    }

    @Test
    fun `the amount is the last standalone number, so names may contain digits`() {
        val entries = BulkEntryParser.parse("7up 30, route 6 bus 40")

        assertEquals(listOf(expense("7up", "30"), expense("Route 6 Bus", "40")), entries)
    }

    @Test
    fun `currency marks around the amount are dropped from the name`() {
        val entries = BulkEntryParser.parse("uber 120tk, ৳50 rickshaw, tk 15 tea, lunch 250 taka, bus 30/-")

        assertEquals(
            listOf(
                expense("Uber", "120"),
                expense("Rickshaw", "50"),
                expense("Tea", "15"),
                expense("Lunch", "250"),
                expense("Bus", "30"),
            ),
            entries,
        )
    }

    @Test
    fun `decimals and bengali digits are read`() {
        val entries = BulkEntryParser.parse("coffee 180.50, চা ২০")

        assertEquals(listOf(expense("Coffee", "180.50"), expense("চা", "20")), entries)
    }

    @Test
    fun `an item without an amount is kept for the user to fix`() {
        val entries = BulkEntryParser.parse("uber, tea 0, 120")

        assertEquals(listOf(Entry("Uber", null, false), Entry("Tea", null, false), expense("", "120")), entries)
    }

    @Test
    fun `upper and mixed case names are left as typed`() {
        val entries = BulkEntryParser.parse("KFC 650, iPhone case 900, star kabab 400")

        assertEquals(listOf("KFC", "iPhone Case", "Star Kabab"), entries.map { it.merchant })
    }

    @Test
    fun `stray separators and dashes are ignored`() {
        val entries = BulkEntryParser.parse(" , tea - 20,, ;\n- \n")

        assertEquals(listOf(expense("Tea", "20")), entries)
    }
}
