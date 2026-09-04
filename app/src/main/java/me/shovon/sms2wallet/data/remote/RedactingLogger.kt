package me.shovon.sms2wallet.data.remote

import io.ktor.client.plugins.logging.Logger

/**
 * Header patterns that carry a credential, with what to replace each match with.
 *
 * Every credential this app sends must appear here. `Authorization` covers the Wallet API's
 * bearer JWT; `x-goog-api-key` covers the Gemini key, which Google takes as a header rather
 * than a bearer token and which Ktor would otherwise print verbatim.
 */
private val SENSITIVE_HEADERS: List<Pair<Regex, String>> = listOf(
    Regex("(?i)Authorization:\\s*Bearer\\s+\\S+") to "Authorization: Bearer <redacted>",
    Regex("(?i)x-goog-api-key:\\s*\\S+") to "x-goog-api-key: <redacted>",
)

/** Replaces any credential in [line] with a fixed placeholder. Pure/testable, no I/O. */
internal fun redactSensitiveHeaders(line: String): String =
    SENSITIVE_HEADERS.fold(line) { redacted, (pattern, replacement) ->
        redacted.replace(pattern, replacement)
    }

/**
 * [Logger] used by the Ktor `Logging` plugin in [KtorWalletApiClient] and
 * [GeminiNaturalLanguageParser].
 *
 * Both clients log at HEADERS level, which would otherwise print their credentials in
 * plaintext to logcat. This wrapper redacts them before the line reaches [sink].
 *
 * HEADERS level also means no request body is logged - so the phrase the user typed, and the
 * category and account names sent with it, never reach the log either.
 */
internal class RedactingLogger(private val sink: (String) -> Unit = ::println) : Logger {
    override fun log(message: String) {
        sink(redactSensitiveHeaders(message))
    }
}
