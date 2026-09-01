package me.shovon.sms2wallet.data.remote

import io.ktor.client.plugins.logging.Logger

/**
 * Matches an `Authorization: Bearer <token>` header value anywhere in a log
 * line, case-insensitively, so it can be masked out.
 */
private val AUTHORIZATION_HEADER_REGEX = Regex("(?i)Authorization:\\s*Bearer\\s+\\S+")

/** Replaces any bearer token in [line] with a fixed placeholder. Pure/testable, no I/O. */
internal fun redactAuthorizationHeader(line: String): String =
    line.replace(AUTHORIZATION_HEADER_REGEX, "Authorization: Bearer <redacted>")

/**
 * [Logger] used by the Ktor `Logging` plugin in [KtorWalletApiClient].
 *
 * The Wallet API token is a bearer JWT sent on every request; Ktor's HEADERS
 * log level would otherwise print it in plaintext. This wrapper redacts it
 * before the line reaches [sink]. It never receives or logs SMS bodies —
 * those live entirely outside this module.
 */
internal class RedactingLogger(private val sink: (String) -> Unit = ::println) : Logger {
    override fun log(message: String) {
        sink(redactAuthorizationHeader(message))
    }
}
