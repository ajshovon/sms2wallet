package me.shovon.sms2wallet.remote

import me.shovon.sms2wallet.data.remote.redactAuthorizationHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pure unit tests for the log-line redaction used by [me.shovon.sms2wallet.data.remote.RedactingLogger]. */
class RedactingLoggerTest {

    @Test
    fun `redacts bearer token from a log line`() {
        val line = "REQUEST: GET https://rest.budgetbakers.com/wallet/v1/api/accounts\n" +
            "Authorization: Bearer synthetic.test.token.value\n" +
            "Accept: application/json"

        val redacted = redactAuthorizationHeader(line)

        assertFalse(redacted.contains("synthetic.test.token.value"))
        assertEquals(
            "REQUEST: GET https://rest.budgetbakers.com/wallet/v1/api/accounts\n" +
                "Authorization: Bearer <redacted>\n" +
                "Accept: application/json",
            redacted,
        )
    }

    @Test
    fun `leaves lines without an authorization header untouched`() {
        val line = "RESPONSE: 200 OK"

        assertEquals(line, redactAuthorizationHeader(line))
    }
}
