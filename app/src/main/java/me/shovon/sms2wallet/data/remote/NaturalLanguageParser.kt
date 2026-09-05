package me.shovon.sms2wallet.data.remote

import me.shovon.sms2wallet.domain.nlp.CategoryPrompt
import me.shovon.sms2wallet.domain.nlp.ParsedNlTransaction

/**
 * Turns a phrase the user typed into one structured transaction.
 *
 * An interface so the Gemini implementation can be swapped for a `MockEngine`-backed one in
 * tests, and so the rest of the app never imports a vendor's types.
 */
interface NaturalLanguageParser {

    /**
     * @param categoryNames names the model may choose a category from. Empty means the user has
     *   withheld them, and the model must not be asked for a category at all.
     * @param accountNames likewise for accounts.
     */
    suspend fun parse(
        input: String,
        categoryNames: List<String>,
        accountNames: List<String>,
        model: String,
    ): NlParseResult

    /**
     * Assigns a category to each of [subjects] in a single request.
     *
     * Batched deliberately: a review queue holds many rows, and one call per row would be both
     * slow and the fastest way to exhaust a free-tier per-minute quota.
     *
     * @param categoryLabels the labels the model may choose from; must be non-empty.
     * @return the chosen label per merchant. Merchants the model declined to classify are
     *   absent from the map rather than present with a guess.
     */
    suspend fun classify(
        subjects: List<CategoryPrompt.Subject>,
        categoryLabels: List<String>,
        model: String,
    ): CategorySuggestionResult

    /**
     * Checks the stored key and [model] without generating anything.
     *
     * @return null when both are usable, otherwise a message to show the user.
     */
    suspend fun verify(model: String): String?
}

/**
 * Outcome of one batch classification.
 *
 * [Success] can carry fewer entries than were asked for - that is the prompt working, not a
 * failure - so callers must treat a missing merchant as "no suggestion", never as an error.
 */
sealed interface CategorySuggestionResult {

    data class Success(val labelByMerchant: Map<String, String>) : CategorySuggestionResult

    data object NotConfigured : CategorySuggestionResult

    data object InvalidApiKey : CategorySuggestionResult

    data class HttpError(val status: Int, val message: String?) : CategorySuggestionResult

    data class NetworkError(val message: String?) : CategorySuggestionResult
}

/**
 * Outcome of one natural-language parse.
 *
 * [InvalidApiKey] and [EmptyResult] are split out from [HttpError] because they are the two the
 * user can actually act on - one means "fix your key in settings", the other means "rephrase
 * it" - and collapsing either into a generic failure would leave them stuck.
 */
sealed interface NlParseResult {

    data class Success(val transaction: ParsedNlTransaction) : NlParseResult

    /** No API key is stored, so nothing was sent. */
    data object NotConfigured : NlParseResult

    /** HTTP 400/401/403 from Google: the key is missing, malformed, revoked, or lacks access. */
    data object InvalidApiKey : NlParseResult

    /** The call succeeded but produced no usable transaction (unparseable phrase, or filtered). */
    data object EmptyResult : NlParseResult

    data class HttpError(val status: Int, val message: String?) : NlParseResult

    data class NetworkError(val message: String?) : NlParseResult
}
