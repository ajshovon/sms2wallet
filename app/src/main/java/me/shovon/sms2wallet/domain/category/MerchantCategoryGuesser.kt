package me.shovon.sms2wallet.domain.category

import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity

/**
 * Guesses a Wallet category from a merchant name.
 *
 * ## Why this is a two-step map rather than a table of category ids
 *
 * Category ids are per-Wallet-account, so a shipped table of ids would match nobody. Category
 * *names* are not fixed either - a user may have "Groceries", "Food & groceries", or the
 * localised equivalent. So the built-in table maps merchant keywords to a set of **category name
 * hints**, and those hints are then matched against whatever categories the user actually has.
 * If none match, the guess is simply absent and the picker stays empty, which is the honest
 * outcome - never a wrong category silently attached to someone's money.
 *
 * Rules are ordered: the first entry whose merchant keyword appears wins, so put the specific
 * ones above the generic. Matching is case-insensitive substring on both sides.
 */
object MerchantCategoryGuesser {

    /**
     * merchant keywords -> candidate category-name hints, most specific first.
     *
     * Bangladeshi chains are listed alongside the generic English terms because the merchant
     * strings in BD transaction SMS are mostly brand names, not category words.
     */
    private val RULES: List<Rule> = listOf(
        Rule(
            merchants = listOf("shwapno", "agora", "meena bazar", "meenabazar", "unimart", "daily shopping", "grocer", "supershop", "super shop"),
            categoryHints = listOf("grocer", "food"),
        ),
        Rule(
            merchants = listOf("pharma", "lazz", "medicine", "hospital", "clinic", "diagnostic", "labaid", "popular diagnostic"),
            categoryHints = listOf("health", "pharm", "medic"),
        ),
        Rule(
            merchants = listOf("uber", "pathao", "obhai", "shohoz", "cng", "rickshaw", "bus ticket", "train"),
            categoryHints = listOf("transport", "taxi", "travel"),
        ),
        Rule(
            merchants = listOf("padma oil", "jamuna oil", "petrol", "octane", "fuel", "filling station"),
            categoryHints = listOf("fuel", "transport"),
        ),
        Rule(
            merchants = listOf("restaurant", "cafe", "coffee", "kfc", "pizza", "burger", "bbq", "sultan", "dhaba", "food panda", "foodpanda"),
            categoryHints = listOf("dining", "restaurant", "food"),
        ),
        Rule(
            merchants = listOf("daraz", "chaldal", "rokomari", "ajkerdeal", "pickaboo", "othoba"),
            categoryHints = listOf("shopping", "electronic"),
        ),
        Rule(
            merchants = listOf("grameenphone", "robi", "banglalink", "airtel", "teletalk", "recharge", "topup", "top up"),
            categoryHints = listOf("mobile", "phone", "communication", "recharge"),
        ),
        Rule(
            merchants = listOf("desco", "dpdc", "wasa", "titas", "palli bidyut", "electricity", "gas bill", "water bill", "utility"),
            categoryHints = listOf("utilit", "bill", "energ"),
        ),
        Rule(
            merchants = listOf("link3", "amber it", "carnival", "dot internet", "broadband", "internet"),
            categoryHints = listOf("internet", "communication", "bill"),
        ),
        Rule(
            merchants = listOf("school", "college", "university", "tuition", "coaching", "academy"),
            categoryHints = listOf("education", "school"),
        ),
        Rule(
            merchants = listOf("salary", "payroll", "disbursement"),
            categoryHints = listOf("salary", "income", "wage"),
        ),
        Rule(
            merchants = listOf("aarong", "yellow", "ecstasy", "sailor", "clothing", "fashion", "apparel"),
            categoryHints = listOf("cloth", "shopping", "apparel"),
        ),
    )

    /**
     * The id of the best-matching category from [categories], or null when nothing matches.
     *
     * @param merchant the parsed counterparty; a blank or unknown merchant yields null.
     */
    fun guess(merchant: String?, categories: List<WalletCategoryEntity>): String? {
        val needle = merchant?.trim()?.lowercase().orEmpty()
        if (needle.isEmpty() || categories.isEmpty()) return null

        val rule = RULES.firstOrNull { rule ->
            rule.merchants.any { needle.contains(it) }
        } ?: return null

        // Hints are ordered by preference, so the first hint that matches any of the user's
        // categories wins - "grocer" before the broader "food".
        rule.categoryHints.forEach { hint ->
            val match = categories.firstOrNull { it.name.lowercase().contains(hint) }
            if (match != null) return match.id
        }
        return null
    }

    private data class Rule(
        val merchants: List<String>,
        val categoryHints: List<String>,
    )
}
