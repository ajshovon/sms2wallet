package me.shovon.sms2wallet.domain.nlp

/**
 * Instruction text for classifying merchants into the user's own categories.
 *
 * Separate from [NlPrompt] because it is a different job with a different failure cost. Parsing
 * a typed phrase invents a whole transaction and the user reads it back before saving;
 * classification only fills one field, often for many rows at once, where a confident wrong
 * answer is likely to be accepted without a second look. So this prompt pushes harder on
 * declining than on covering everything.
 *
 * Kept pure so the exact bytes leaving the device are testable.
 */
object CategoryPrompt {

    /** One merchant to classify, with the direction of the money. */
    data class Subject(val merchant: String, val isIncome: Boolean)

    fun systemInstruction(categoryLabels: List<String>): String = buildString {
        appendLine(
            "You assign a spending category to each merchant in a list. Return only JSON matching " +
                "the provided schema: no explanation, no markdown, no commentary."
        )
        appendLine()
        appendLine(
            "Choose the single best fit from the categories below. The value must match one of " +
                "them exactly."
        )
        appendLine("Categories: " + categoryLabels.joinToString(", "))
        appendLine()
        appendLine(
            "Leave a merchant out of the response entirely when no category is a clear fit. An " +
                "omission costs the user one tap; a wrong category is filed as fact and is " +
                "unlikely to be noticed. Prefer omitting."
        )
        appendLine(
            "Each entry says whether money came in or went out. Money coming in is usually " +
                "salary, a refund or a transfer received, and rarely a shopping category."
        )
        appendLine(
            "Merchant names come from bank SMS, so they are often abbreviated, upper-case, or " +
                "carry a branch or city suffix. Judge them on the recognisable brand or business " +
                "type and ignore trailing reference codes."
        )
    }.trim()

    /** The user-turn payload: the merchants to classify, one per line. */
    fun userContent(subjects: List<Subject>): String = subjects.joinToString("\n") { subject ->
        val direction = if (subject.isIncome) "money in" else "money out"
        "${subject.merchant} ($direction)"
    }
}
