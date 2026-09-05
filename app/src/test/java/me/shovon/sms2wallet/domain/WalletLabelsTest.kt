package me.shovon.sms2wallet.domain

import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity
import me.shovon.sms2wallet.domain.model.WalletLabels
import me.shovon.sms2wallet.domain.model.idFor
import me.shovon.sms2wallet.domain.model.labelFor
import me.shovon.sms2wallet.domain.model.labels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The property that matters: a label resolves to exactly one id. Anything less files a
 * transaction against the wrong category, silently, in the user's real Wallet.
 */
class WalletLabelsTest {

    private fun category(id: String, name: String, parentId: String? = null) =
        WalletCategoryEntity(id = id, name = name, systemId = null, parentId = parentId, color = null, cachedAt = 0)

    private fun account(id: String, name: String, currency: String = "BDT") =
        WalletAccountEntity(id = id, name = name, currencyCode = currency, accountType = "GENERAL", cachedAt = 0)

    @Test
    fun `unique names are left alone`() {
        val labels = WalletLabels.forCategories(
            listOf(category("a", "Groceries"), category("b", "Transport"))
        )

        assertEquals(listOf("Groceries", "Transport"), labels.labels())
    }

    @Test
    fun `same-named categories are told apart by parent`() {
        val labels = WalletLabels.forCategories(
            listOf(
                category("food", "Food"),
                category("transport", "Transport"),
                category("o1", "Other", parentId = "food"),
                category("o2", "Other", parentId = "transport"),
            )
        )

        assertEquals("Other (Food)", labels.labelFor("o1"))
        assertEquals("Other (Transport)", labels.labelFor("o2"))
    }

    @Test
    fun `every label maps back to exactly one id`() {
        val labels = WalletLabels.forCategories(
            listOf(
                category("food", "Food"),
                category("o1", "Other", parentId = "food"),
                category("o2", "Other", parentId = "food"),
                category("o3", "Other"),
            )
        )

        // Three categories share both a name and (for two of them) a parent.
        val ids = labels.labels().map { labels.idFor(it) }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(listOf("food", "o1", "o2", "o3").sorted(), ids.filterNotNull().sorted())
    }

    @Test
    fun `labels are unique even when a qualifier collides`() {
        val labels = WalletLabels.forCategories(
            listOf(
                category("p", "Food"),
                category("a", "Other", parentId = "p"),
                category("b", "Other", parentId = "p"),
            )
        )

        assertEquals(labels.labels().size, labels.labels().distinct().size)
    }

    @Test
    fun `same-named accounts are told apart by currency`() {
        val labels = WalletLabels.forAccounts(
            listOf(account("a", "Cash", "BDT"), account("b", "Cash", "USD"))
        )

        assertEquals("Cash (BDT)", labels.labelFor("a"))
        assertEquals("Cash (USD)", labels.labelFor("b"))
    }

    @Test
    fun `an unknown label or id resolves to nothing rather than to the first entry`() {
        val labels = WalletLabels.forCategories(listOf(category("a", "Groceries")))

        assertNull(labels.idFor("Deleted category"))
        assertNull(labels.labelFor("no-such-id"))
        assertNull(labels.labelFor(null))
    }
}
