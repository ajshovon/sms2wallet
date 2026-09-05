package me.shovon.sms2wallet.domain.category

import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity

/**
 * The on-device half of category resolution: a learned rule first, then the built-in merchant
 * table. Returns a Wallet category id, or null when neither knows.
 *
 * Exists so the AI suggestion path answers the same way ingest does, and - more importantly -
 * so an API call is only ever spent on a merchant nothing local can already answer.
 */
object LocalCategoryResolver {

    fun resolve(
        merchant: String?,
        rules: List<CategoryRuleEntity>,
        categories: List<WalletCategoryEntity>,
    ): String? {
        if (merchant.isNullOrBlank()) return null

        // A rule is an explicit decision about this merchant, so it outranks the built-in table.
        rules.firstOrNull { merchant.contains(it.keyword, ignoreCase = true) }
            ?.let { return it.walletCategoryId }

        return MerchantCategoryGuesser.guess(merchant, categories)
    }

    /**
     * The keyword to remember a merchant by.
     *
     * Bank SMS merchant strings carry branch names, city suffixes and reference numbers
     * ("SHWAPNO SUPERSHOP DHANMONDI 4412"), so storing the whole string would match nothing
     * next time. The leading words are the brand; digits and very short fragments are dropped
     * because they match far too much.
     */
    fun keywordFor(merchant: String?, maxWords: Int = 2): String? {
        val words = merchant?.trim()
            ?.split(Regex("[^\\p{L}\\p{N}]+"))
            ?.filter { it.length >= MIN_WORD_LENGTH && !it.all(Char::isDigit) }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return words.take(maxWords).joinToString(" ")
    }

    private const val MIN_WORD_LENGTH = 3
}
