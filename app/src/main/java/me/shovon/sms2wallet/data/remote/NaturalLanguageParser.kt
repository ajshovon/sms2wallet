package me.shovon.sms2wallet.data.remote

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
     * Checks the stored key and [model] without generating anything.
     *
     * @return null when both are usable, otherwise a message to show the user.
     */
    suspend fun verify(model: String): String?
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
