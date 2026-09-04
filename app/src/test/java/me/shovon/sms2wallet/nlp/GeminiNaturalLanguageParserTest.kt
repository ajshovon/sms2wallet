package me.shovon.sms2wallet.nlp

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import me.shovon.sms2wallet.data.remote.GeminiNaturalLanguageParser
import me.shovon.sms2wallet.data.remote.NlParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [GeminiNaturalLanguageParser] against a [MockEngine].
 *
 * The important assertions here are about the *request*: this is the one place in the app that
 * sends user data to a third party, so what goes into the body is behaviour worth pinning, not
 * an implementation detail.
 *
 * All keys and names below are synthetic.
 */
class GeminiNaturalLanguageParserTest {

    private val fixedNow = LocalDateTime.of(2026, 9, 3, 14, 30)

    /** Captures the outgoing request so assertions can be made about what was sent. */
    private class Capture {
        var request: HttpRequestData? = null
        var body: String = ""
    }

    private fun parserReturning(payload: String, capture: Capture = Capture()) =
        GeminiNaturalLanguageParser(
            engine = MockEngine { request ->
                capture.request = request
                capture.body = String((request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes())
                respond(
                    content = ByteReadChannel(
                        """{"candidates":[{"content":{"parts":[{"text":${payload.asJsonString()}}]}}]}"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            apiKeyProvider = { "synthetic-key" },
            now = { fixedNow },
        ) to capture

    private fun String.asJsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun `negative amount is read as an expense in magnitude`() = runTest {
        val (parser, _) = parserReturning("""{"amount":-120,"title":"Uber"}""")

        val result = parser.parse("uber 120", emptyList(), emptyList(), "gemini-flash-latest")

        val transaction = (result as NlParseResult.Success).transaction
        assertEquals(BigDecimal("120"), transaction.amount)
        assertFalse(transaction.isIncome)
        assertEquals("Uber", transaction.title)
    }

    @Test
    fun `positive amount is read as income`() = runTest {
        val (parser, _) = parserReturning("""{"amount":25000,"title":"Salary"}""")

        val result = parser.parse("got salary 25000", emptyList(), emptyList(), "gemini-flash-latest")

        assertTrue((result as NlParseResult.Success).transaction.isIncome)
    }

    @Test
    fun `decimal amounts keep their exact value`() = runTest {
        val (parser, _) = parserReturning("""{"amount":-120.35,"title":"Shop"}""")

        val result = parser.parse("shop 120.35", emptyList(), emptyList(), "gemini-flash-latest")

        // Via Double this would be 120.34999999999999...; money must not go through a Double.
        assertEquals(BigDecimal("120.35"), (result as NlParseResult.Success).transaction.amount)
    }

    @Test
    fun `category names are sent as a schema enum when shared`() = runTest {
        val (parser, capture) = parserReturning("""{"amount":-120,"title":"Uber","category":"Transport"}""")

        parser.parse("uber 120", listOf("Transport", "Groceries"), emptyList(), "gemini-flash-latest")

        assertTrue(capture.body.contains("\"Transport\""))
        assertTrue(capture.body.contains("\"enum\""))
    }

    @Test
    fun `withheld account names appear nowhere in the request`() = runTest {
        val (parser, capture) = parserReturning("""{"amount":-120,"title":"Uber"}""")

        parser.parse(
            input = "uber 120",
            categoryNames = listOf("Transport"),
            // The caller withholds accounts by passing none - the parser must not invent a
            // place for them, in the schema or in the prompt.
            accountNames = emptyList(),
            model = "gemini-flash-latest",
        )

        assertFalse(capture.body.contains("account"))
        assertFalse(capture.body.contains("bKash"))
    }

    @Test
    fun `the api key travels in a header and never in the url`() = runTest {
        val (parser, capture) = parserReturning("""{"amount":-120,"title":"Uber"}""")

        parser.parse("uber 120", emptyList(), emptyList(), "gemini-flash-latest")

        assertEquals("synthetic-key", capture.request?.headers?.get("x-goog-api-key"))
        // A key in the query string would be logged by every proxy on the path.
        assertFalse(capture.request?.url.toString().contains("synthetic-key"))
    }

    @Test
    fun `a rejected key is reported as such rather than as a generic failure`() = runTest {
        val parser = GeminiNaturalLanguageParser(
            engine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"error":{"code":400,"message":"API key not valid"}}"""),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            apiKeyProvider = { "synthetic-bad-key" },
            now = { fixedNow },
        )

        assertEquals(
            NlParseResult.InvalidApiKey,
            parser.parse("uber 120", emptyList(), emptyList(), "gemini-flash-latest"),
        )
    }

    @Test
    fun `no stored key means nothing is sent at all`() = runTest {
        var called = false
        val parser = GeminiNaturalLanguageParser(
            engine = MockEngine {
                called = true
                respond(ByteReadChannel("{}"), HttpStatusCode.OK)
            },
            apiKeyProvider = { null },
            now = { fixedNow },
        )

        assertEquals(
            NlParseResult.NotConfigured,
            parser.parse("uber 120", emptyList(), emptyList(), "gemini-flash-latest"),
        )
        assertFalse(called)
    }

    @Test
    fun `a filtered or empty candidate list is an empty result, not a crash`() = runTest {
        val parser = GeminiNaturalLanguageParser(
            engine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"candidates":[]}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            apiKeyProvider = { "synthetic-key" },
            now = { fixedNow },
        )

        assertEquals(
            NlParseResult.EmptyResult,
            parser.parse("hello", emptyList(), emptyList(), "gemini-flash-latest"),
        )
    }

    @Test
    fun `a phrase with no merchant and no amount yields nothing`() = runTest {
        val (parser, _) = parserReturning("""{"amount":0,"title":""}""")

        assertEquals(
            NlParseResult.EmptyResult,
            parser.parse("hello there", emptyList(), emptyList(), "gemini-flash-latest"),
        )
    }
}
