package me.shovon.sms2wallet.presentation.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats amounts as Bangladeshi Taka (BDT) strings, e.g. `৳1,08,650.00`.
 *
 * Bangladesh (like India) groups digits as: the last three digits together, then every
 * subsequent group of two digits moving left (lakh/crore style) - NOT the "every three
 * digits" grouping [java.text.NumberFormat] uses for most JVM locales, including `en_IN` on
 * many Android/JVM ICU builds. This formatter implements the grouping manually so the output
 * is correct regardless of the runtime's locale data.
 *
 * Examples:
 *  - 650          -> ৳650.00
 *  - 1000         -> ৳1,000.00
 *  - 108650       -> ৳1,08,650.00
 *  - 1234567      -> ৳12,34,567.00
 *  - 123456789    -> ৳12,34,56,789.00
 */
object MoneyFormatter {

    /** Bengali/Bangladeshi Taka currency symbol. */
    const val TAKA_SYMBOL = "৳"

    /**
     * Formats [amount] as a BDT string with the [TAKA_SYMBOL] prefix, Bangladeshi digit
     * grouping, and exactly two decimal places. Negative amounts render as `-৳1,000.00`.
     */
    fun formatBdt(amount: BigDecimal): String {
        val rounded = amount.setScale(2, RoundingMode.HALF_UP)
        val isNegative = rounded.signum() < 0
        val unsigned = rounded.abs()

        val plain = unsigned.toPlainString()
        val dotIndex = plain.indexOf('.')
        val integerPart = if (dotIndex >= 0) plain.substring(0, dotIndex) else plain
        val fractionPart = if (dotIndex >= 0) plain.substring(dotIndex + 1) else "00"

        val grouped = groupIndianStyle(integerPart)
        val sign = if (isNegative) "-" else ""
        return "$sign$TAKA_SYMBOL$grouped.$fractionPart"
    }

    /** Convenience overload for call sites that only have a [Double] (e.g. preview/sample data). */
    fun formatBdt(amount: Double): String = formatBdt(BigDecimal.valueOf(amount))

    /** Convenience overload for whole-number amounts. */
    fun formatBdt(amount: Long): String = formatBdt(BigDecimal.valueOf(amount))

    /**
     * Groups a non-negative, no-sign, no-decimal digit string using Bangladeshi/Indian
     * digit grouping: the last three digits form one group, then every group of two digits
     * moving further left, separated by commas.
     */
    internal fun groupIndianStyle(integerDigits: String): String {
        if (integerDigits.length <= 3) return integerDigits

        val lastThree = integerDigits.substring(integerDigits.length - 3)
        val remainder = integerDigits.substring(0, integerDigits.length - 3)

        val groups = mutableListOf<String>()
        var index = remainder.length
        while (index > 0) {
            val start = maxOf(0, index - 2)
            groups.add(0, remainder.substring(start, index))
            index = start
        }

        return groups.joinToString(separator = ",") + "," + lastThree
    }
}
