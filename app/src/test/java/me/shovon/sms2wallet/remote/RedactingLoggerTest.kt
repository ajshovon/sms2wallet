package me.shovon.sms2wallet.remote

import me.shovon.sms2wallet.data.remote.redactSensitiveHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure unit tests for the log-line redaction used by [me.shovon.sms2wallet.data.remote.RedactingLogger]. */
class RedactingLoggerTest {

    @Test
    fun `redacts bearer token from a log line`() {
        val line = "REQUEST: GET https://rest.budgetbakers.com/wallet/v1/api/accounts\n" +
            "Authorization: Bearer synthetic.test.token.value\n" +
            "Accept: application/json"

        val redacted = redactSensitiveHeaders(line)

        assertFalse(redacted.contains("synthetic.test.token.value"))
        assertEquals(
            "REQUEST: GET https://rest.budgetbakers.com/wallet/v1/api/accounts\n" +
                "Authorization: Bearer <redacted>\n" +
                "Accept: application/json",
            redacted,
        )
    }

    @Test
    fun `redacts the gemini api key, which is not a bearer token`() {
        val line = "REQUEST: https://generativelanguage.googleapis.com/v1beta/models/x:generateContent\n" +
            "x-goog-api-key: AIzaSyNOTAREALKEY_synthetic_0000000000"

        val redacted = redactSensitiveHeaders(line)

        assertFalse(redacted.contains("AIzaSyNOTAREALKEY_synthetic_0000000000"))
        assertTrue(redacted.contains("x-goog-api-key: <redacted>"))
    }

    @Test
    fun `leaves lines without an authorization header untouched`() {
        val line = "RESPONSE: 200 OK"

        assertEquals(line, redactSensitiveHeaders(line))
    }
}
