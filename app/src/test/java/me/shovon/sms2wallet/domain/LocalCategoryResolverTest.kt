package me.shovon.sms2wallet.domain

import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity
import me.shovon.sms2wallet.domain.category.LocalCategoryResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCategoryResolverTest {

    private fun category(id: String, name: String) =
        WalletCategoryEntity(id = id, name = name, systemId = null, parentId = null, color = null, cachedAt = 0)

    private fun rule(keyword: String, categoryId: String) =
        CategoryRuleEntity(id = 0, keyword = keyword, walletCategoryId = categoryId, priority = 100, bankName = null)

    private val categories = listOf(category("c-groc", "Groceries"), category("c-health", "Healthcare"))

    @Test
    fun `a learned rule wins over the built-in table`() {
        // Shwapno is a grocer in the built-in map; the user's rule must still win.
        val resolved = LocalCategoryResolver.resolve(
            merchant = "SHWAPNO SUPERSHOP",
            rules = listOf(rule("SHWAPNO", "c-health")),
            categories = categories,
        )

        assertEquals("c-health", resolved)
    }

    @Test
    fun `falls back to the built-in table when no rule matches`() {
        val resolved = LocalCategoryResolver.resolve("SHWAPNO SUPERSHOP", emptyList(), categories)

        assertEquals("c-groc", resolved)
    }

    @Test
    fun `returns null when nothing local knows, so the caller can decide to ask the model`() {
        assertNull(LocalCategoryResolver.resolve("ZZZ UNKNOWN VENDOR", emptyList(), categories))
        assertNull(LocalCategoryResolver.resolve(null, emptyList(), categories))
    }

    @Test
    fun `keyword strips branch noise so it matches the next visit`() {
        // The stored keyword has to match a differently-suffixed string next month.
        val keyword = LocalCategoryResolver.keywordFor("SHWAPNO SUPERSHOP DHANMONDI 4412")

        assertEquals("SHWAPNO SUPERSHOP", keyword)
    }

    @Test
    fun `keyword ignores digits and fragments that would match everything`() {
        assertEquals("PATHAO", LocalCategoryResolver.keywordFor("PATHAO 12 4x"))
        assertNull(LocalCategoryResolver.keywordFor("12 34"))
        assertNull(LocalCategoryResolver.keywordFor("   "))
    }
}
