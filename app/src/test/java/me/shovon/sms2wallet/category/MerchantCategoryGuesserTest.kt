package me.shovon.sms2wallet.category

import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity
import me.shovon.sms2wallet.domain.category.MerchantCategoryGuesser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the built-in merchant -> category map.
 *
 * The behaviour that matters most is what happens when it is *unsure*: attaching a plausible but
 * wrong category to someone's spending is worse than attaching none, because a wrong one looks
 * right and gets pushed without a second glance.
 */
class MerchantCategoryGuesserTest {

    private fun categories(vararg names: String) =
        names.mapIndexed { i, n -> WalletCategoryEntity(id = "cat-$i", name = n, systemId = null, parentId = null, color = null, cachedAt = 0L) }

    @Test
    fun `maps a known BD supermarket to the user's groceries category`() {
        val result = MerchantCategoryGuesser.guess("SHWAPNO SUPERSHOP", categories("Dining out", "Groceries"))
        assertEquals("cat-1", result)
    }

    @Test
    fun `matching is case-insensitive and substring-based`() {
        val cats = categories("Groceries")
        assertEquals("cat-0", MerchantCategoryGuesser.guess("payment to agora ltd", cats))
    }

    @Test
    fun `prefers the more specific category hint over the broader one`() {
        // "grocer" is listed before "food", so a user with both gets Groceries, not Food.
        val cats = categories("Food & drink", "Groceries")
        assertEquals("cat-1", MerchantCategoryGuesser.guess("MEENA BAZAR", cats))
    }

    @Test
    fun `falls back to a broader hint when the specific one does not exist`() {
        val cats = categories("Food & drink")
        assertEquals("cat-0", MerchantCategoryGuesser.guess("SHWAPNO", cats))
    }

    @Test
    fun `returns null when the merchant is unknown`() {
        // Guessing here would put a wrong category on real money; no guess is the right answer.
        assertNull(MerchantCategoryGuesser.guess("SOME RANDOM SHOP", categories("Groceries", "Transport")))
    }

    @Test
    fun `returns null when the user has no matching category`() {
        // The merchant is recognised, but this Wallet has nothing grocery-like to file it under.
        assertNull(MerchantCategoryGuesser.guess("SHWAPNO", categories("Transport", "Salary")))
    }

    @Test
    fun `returns null for a blank merchant or an empty category list`() {
        assertNull(MerchantCategoryGuesser.guess("", categories("Groceries")))
        assertNull(MerchantCategoryGuesser.guess(null, categories("Groceries")))
        assertNull(MerchantCategoryGuesser.guess("SHWAPNO", emptyList()))
    }

    @Test
    fun `pharmacies map to healthcare and ride-hailing to transport`() {
        val cats = categories("Healthcare", "Transport", "Groceries")
        assertEquals("cat-0", MerchantCategoryGuesser.guess("LAZZ PHARMA", cats))
        assertEquals("cat-1", MerchantCategoryGuesser.guess("PATHAO RIDES", cats))
    }
}
