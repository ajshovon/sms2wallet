package me.shovon.sms2wallet.domain.nlp

import java.math.BigDecimal

/**
 * Splits a day's worth of typed spending - "uber 120, tea 20, cig 10" - into one entry per item,
 * entirely on-device.
 *
 * Deliberately simple and predictable rather than clever: an item is some words and an amount.
 * Anything it cannot read comes back with a null [Entry.amount] for the user to fix in the review
 * list, rather than being guessed at or silently dropped.
 */
object BulkEntryParser {

    /** One typed item. [amount] is null when the item states none (or states zero). */
    data class Entry(
        val merchant: String,
        val amount: BigDecimal?,
        val isIncome: Boolean,
    )

    fun parse(input: String): List<Entry> =
        split(normaliseDigits(input)).mapNotNull(::parseItem)

    /**
     * Items are separated by new lines, semicolons and commas - except a comma between two digits,
     * which is a thousands separator in "1,200" and in the lakh grouping of "1,20,000". Writing
     * "120,130" without a space therefore reads as one number, which is the rarer mistake.
     */
    internal fun split(input: String): List<String> =
        input.split(ITEM_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseItem(item: String): Entry? {
        // The last standalone number is the amount: people write "uber 120", and the name may
        // itself contain a number ("7up", "Route 6").
        val match = AMOUNT.findAll(item).lastOrNull()
        val amount = match?.groupValues?.get(2)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 }
        val isIncome = item.startsWith('+') || match?.groupValues?.get(1) == "+"

        val merchant = (if (match == null) item else item.removeRange(match.range))
            .trim()
            .trim(*EDGE_PUNCTUATION)
            .trim()
            .replace(WHITESPACE, " ")
            .let(::capitalise)

        if (merchant.isEmpty() && match == null) return null
        return Entry(merchant = merchant, amount = amount, isIncome = isIncome)
    }

    /**
     * "uber" becomes "Uber", but "KFC" and "iPhone" are left as typed: only words the user wrote
     * entirely in lower case are touched.
     */
    private fun capitalise(text: String): String = text.split(' ').joinToString(" ") { word ->
        if (word == word.lowercase()) word.replaceFirstChar { it.titlecase() } else word
    }

    /** Bengali digits to ASCII, so "চা ২০" reads the same as "চা 20". */
    private fun normaliseDigits(input: String): String = buildString(input.length) {
        input.forEach { char ->
            append(if (char in '০'..'৯') '0' + (char - '০') else char)
        }
    }

    private val ITEM_SEPARATOR = Regex("""[\n;]|(?<!\d),|,(?!\d)""")

    /**
     * An optional "+", an optional currency mark, the number, an optional currency suffix - and
     * nothing alphanumeric touching either end, so the 7 in "7up" is not an amount.
     */
    private val AMOUNT = Regex(
        """(?<![\p{L}\p{N}])(\+)?\s*(?:৳|tk\.?|bdt)?\s*(\d[\d,]*(?:\.\d+)?)\s*(?:tk|taka|৳|/-)?(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )

    private val WHITESPACE = Regex("""\s+""")

    private val EDGE_PUNCTUATION = charArrayOf('-', ':', '=', '+', '.', ',', '(', ')')
}
