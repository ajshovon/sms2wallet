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

    @Test
    fun `guesses a system category when matched`() {
        val system = WalletCategoryEntity(
            id = "5c5c4e20-00c8-8000-8000-000000000000",
            name = "Food & Drinks",
            systemId = "food_and_drinks",
            parentId = null,
            color = null,
            cachedAt = 0,
        )

        assertEquals("5c5c4e20-00c8-8000-8000-000000000000", MerchantCategoryGuesser.guess("SHWAPNO SUPERSHOP", listOf(system)))
    }

    @Test
    fun `guesses a system subcategory with parentId`() {
        val root = WalletCategoryEntity(
            id = "sys-root",
            name = "Food & Drinks",
            systemId = "food_and_drinks",
            parentId = null,
            color = null,
            cachedAt = 0,
        )
        val groceries = WalletCategoryEntity(
            id = "sub-groc",
            name = "Groceries",
            systemId = "food_and_drinks__groceries",
            parentId = "sys-root",
            color = null,
            cachedAt = 0,
        )

        assertEquals("sub-groc", MerchantCategoryGuesser.guess("SHWAPNO SUPERSHOP", listOf(root, groceries)))
    }

    @Test
    fun `common quick-add expense words map to correct categories`() {
        val cats = categories("Dining out", "Transportation", "Groceries", "Utilities & Bills", "Shopping")

        // Dining / Food
        assertEquals("cat-0", MerchantCategoryGuesser.guess("dinner", cats))
        assertEquals("cat-0", MerchantCategoryGuesser.guess("lunch at office", cats))
        assertEquals("cat-0", MerchantCategoryGuesser.guess("breakfast", cats))
        assertEquals("cat-0", MerchantCategoryGuesser.guess("coffee with friends", cats))

        // Transport
        assertEquals("cat-1", MerchantCategoryGuesser.guess("taxi fare", cats))
        assertEquals("cat-1", MerchantCategoryGuesser.guess("bus ticket", cats))
        assertEquals("cat-1", MerchantCategoryGuesser.guess("metro rail", cats))

        // Groceries
        assertEquals("cat-2", MerchantCategoryGuesser.guess("groceries", cats))
        assertEquals("cat-2", MerchantCategoryGuesser.guess("supermarket market", cats))

        // Utilities
        assertEquals("cat-3", MerchantCategoryGuesser.guess("electricity bill", cats))
        assertEquals("cat-3", MerchantCategoryGuesser.guess("electric", cats))
    }

    @Test
    fun `uber 120 correctly matches Transportation category when Card payment also exists`() {
        val categories = listOf(
            WalletCategoryEntity(
                id = "cat-trans",
                name = "Transportation",
                systemId = "transportation",
                parentId = null,
                color = null,
                cachedAt = 0,
            ),
            WalletCategoryEntity(
                id = "cat-card",
                name = "Card payment",
                systemId = null,
                parentId = "cat-fin",
                color = null,
                cachedAt = 0,
            )
        )

        val guessed = MerchantCategoryGuesser.guess("uber 120", categories)
        assertEquals("cat-trans", guessed)
    }
}
