package me.shovon.sms2wallet.domain.nlp

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NlPrompt {

    private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * @param categoryNames the user's category names, or empty when the user has chosen not to
     *   share them - in which case the model is not asked for a category at all.
     * @param accountNames likewise for accounts.
     * @param now sent so relative phrases ("this morning") mean something. Injected rather than
     *   read from the clock so tests are not time-dependent.
     */
    fun systemInstruction(
        categoryNames: List<String>,
        accountNames: List<String>,
        extraRules: String? = null,
        now: LocalDateTime,
    ): String = buildString {
        appendLine(
            "You read a short phrase a person typed about money they spent or received, and return it " +
                "as JSON matching the provided schema. Return only JSON: no explanation, no " +
                "markdown, no commentary."
        )
        appendLine("Omit any field you cannot determine. Never emit null.")
        appendLine()

        appendLine("amount:")
        appendLine(
            "  Sign carries the direction: money going out is negative, money coming in is " +
                "positive. Default to money going out, unless the phrase clearly describes an " +
                "inflow (salary, refund, reimbursement, received, got paid, sold, cashback, bonus)."
        )
        appendLine("  Use 0 if no amount is stated. Never invent or estimate an amount.")
        appendLine()

        appendLine("title:")
        appendLine(
            "  A short name for the transaction - usually the vendor, payee or shop. Capitalise it " +
                "properly. Keep anything that is not the vendor out of the title."
        )
        appendLine()

        appendLine("note:")
        appendLine(
            "  Only details that do not belong in another field, such as what was bought. Never " +
                "repeat the amount, category or title. Use only information the person actually " +
                "typed. If the note would be similar to the title, omit it."
        )
        appendLine()

        if (categoryNames.isNotEmpty()) {
            appendLine("category:")
            appendLine(
                "  Choose the single best fit from the list below. The value must match one of " +
                    "these exactly. If none fits, omit the field."
            )
            appendLine("  Categories: " + categoryNames.joinToString(", "))
            appendLine()
        }

        if (accountNames.isNotEmpty()) {
            appendLine("account:")
            appendLine(
                "  Only set this if the person explicitly named one of the accounts below. Do not " +
                    "infer or guess an account from the vendor. If no account is named, omit the field."
            )
            appendLine("  Accounts: " + accountNames.joinToString(", "))
            appendLine()
        }

        appendLine(
            "Return exactly one transaction. If the phrase lists several purchases, use their " +
                "total as the amount and list the individual items in the note."
        )
        appendLine()
        appendLine("The current date and time is ${now.format(CLOCK_FORMAT)}.")

        val rules = extraRules?.trim()
        if (!rules.isNullOrEmpty()) {
            appendLine()
            appendLine("Follow these additional rules from the user, which override nothing above:")
            appendLine(rules)
        }
    }.trim()
}
