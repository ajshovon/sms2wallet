package me.shovon.sms2wallet.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.KtorWalletApiClient
import me.shovon.sms2wallet.data.remote.dto.CreateRecordRequest
import me.shovon.sms2wallet.data.remote.dto.RecordAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for [KtorWalletApiClient] against a [MockEngine].
 *
 * Synthetic fixtures only: token, account/record ids and amounts below are
 * invented for these tests and are not tied to any real person or account.
 */
class KtorWalletApiClientTest {

    private val fakeToken: suspend () -> String? = { "synthetic-test-token" }

    private fun clientWith(engine: MockEngine) =
        KtorWalletApiClient(engine = engine, tokenProvider = fakeToken)

    private fun sampleRequest(
        accountId: String = "acc-test-001",
        amount: Double = -10.0,
    ) = CreateRecordRequest(
        accountId = accountId,
        amount = RecordAmount(amount),
        recordDate = "2026-01-15T00:00:00Z",
        note = "synthetic test note",
    )

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ) = respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                *extraHeaders.map { it.first to listOf(it.second) }.toTypedArray(),
            ),
        )

    // ---- Batch write semantics (requirement 1) --------------------------

    @Test
    fun `createRecords maps 207 partial failure by inputIndex`() = runTest {
        val responseJson = """
            {
              "summary": {"total":3,"succeeded":2,"clientErrors":1,"serverErrors":0,"documentsWritten":2},
              "results": [
                {"inputIndex":0,"success":false,"error":"invalid accountId","errorType":"client_error","fields":["accountId"]},
                {"inputIndex":1,"success":true,"id":"rec-synthetic-001"},
                {"inputIndex":2,"success":true,"id":"rec-synthetic-002"}
              ]
            }
        """.trimIndent()
        val engine = MockEngine { jsonResponse(responseJson, HttpStatusCode(207, "Multi-Status")) }
        val client = clientWith(engine)
        val requests = listOf(
            sampleRequest(accountId = "acc-1", amount = -10.0),
            sampleRequest(accountId = "acc-2", amount = -20.0),
            sampleRequest(accountId = "acc-3", amount = -30.0),
        )

        val result = client.createRecords(requests)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertFalse(data.allSucceeded)
        val failures = data.failuresByInputIndex()
        val successes = data.successesByInputIndex()
        assertEquals(setOf(0), failures.keys)
        assertEquals(setOf(1, 2), successes.keys)
        assertEquals("rec-synthetic-001", successes.getValue(1).id)
        assertEquals("rec-synthetic-002", successes.getValue(2).id)
    }

    @Test
    fun `200 response with a failed item inside results is not full success`() = runTest {
        val responseJson = """
            {
              "summary": {"total":1,"succeeded":0,"clientErrors":1,"serverErrors":0,"documentsWritten":0},
              "results": [
                {"inputIndex":0,"success":false,"error":"category not found","errorType":"client_error","fields":["categoryId"]}
              ]
            }
        """.trimIndent()
        val engine = MockEngine { jsonResponse(responseJson, HttpStatusCode.OK) }
        val client = clientWith(engine)

        val result = client.createRecords(listOf(sampleRequest()))

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertFalse("HTTP 200 must not be read as full success when results[] contains a failure", data.allSucceeded)
        assertTrue(data.successesByInputIndex().isEmpty())
        assertEquals(setOf(0), data.failuresByInputIndex().keys)
    }

    // ---- 409 init_sync_in_progress (requirement 2) -----------------------

    @Test
    fun `409 init_sync_in_progress maps to SyncInProgress with minutes`() = runTest {
        val body = """{"error":"init_sync_in_progress","message":"Initial sync running","retry_after_minutes":5}"""
        val engine = MockEngine { jsonResponse(body, HttpStatusCode.Conflict) }
        val client = clientWith(engine)

        val result = client.validateToken()

        assertEquals(ApiResult.SyncInProgress(5), result)
    }

    @Test
    fun `409 with an unrelated error body is a generic HttpError, not SyncInProgress`() = runTest {
        val body = """{"error":"some_other_conflict","message":"unrelated"}"""
        val engine = MockEngine { jsonResponse(body, HttpStatusCode.Conflict) }
        val client = clientWith(engine)

        val result = client.validateToken()

        assertTrue(result is ApiResult.HttpError)
        assertEquals(409, (result as ApiResult.HttpError).status)
    }

    // ---- 429 rate limiting (requirement 3) -------------------------------

    @Test
    fun `429 with Retry-After maps to RateLimited`() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "120"),
            )
        }
        val client = clientWith(engine)

        val result = client.validateToken()

        assertEquals(ApiResult.RateLimited(120), result)
    }

    @Test
    fun `rate limit headers are tracked and exposed via usageStats`() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"data":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "X-RateLimit-Limit" to listOf("300"),
                    "X-RateLimit-Remaining" to listOf("287"),
                ),
            )
        }
        val client = clientWith(engine)

        client.validateToken()
        val stats = client.usageStats()

        assertTrue(stats is ApiResult.Success)
        val dto = (stats as ApiResult.Success).data
        assertEquals(300, dto.limit)
        assertEquals(287, dto.remaining)
    }

    // ---- 401 (required branch) -------------------------------------------

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val engine = MockEngine { respond(content = ByteReadChannel(""), status = HttpStatusCode.Unauthorized) }
        val client = clientWith(engine)

        val result = client.validateToken()

        assertEquals(ApiResult.Unauthorized, result)
    }

    // ---- Income/expense sign (no explicit type field) ---------------------

    @Test
    fun `negative amount yields expense sign, positive yields income sign`() {
        val expense = sampleRequest(amount = -42.5)
        val income = sampleRequest(amount = 42.5)

        assertTrue(expense.amount.value < 0)
        assertTrue(income.amount.value > 0)
    }

    @Test
    fun `zero amount is rejected before any call`() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleRequest(amount = 0.0)
        }
    }

    // ---- Client-side batch size guard -------------------------------------

    @Test
    fun `createRecords rejects more than 50 items without an HTTP call`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            jsonResponse("""{"summary":{},"results":[]}""", HttpStatusCode.OK)
        }
        val client = clientWith(engine)
        // POST /records is capped at maxItems: 50 in the OpenAPI schema.
        val requests = (1..51).map { sampleRequest(accountId = "acc-$it", amount = -1.0) }

        val result = client.createRecords(requests)

        assertTrue(result is ApiResult.InvalidRequest)
        assertEquals(0, callCount)
    }

    @Test
    fun `createRecords rejects an empty list without an HTTP call`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            jsonResponse("""{"summary":{},"results":[]}""", HttpStatusCode.OK)
        }
        val client = clientWith(engine)

        val result = client.createRecords(emptyList())

        assertTrue(result is ApiResult.InvalidRequest)
        assertEquals(0, callCount)
    }

    // ---- Pagination ---------------------------------------------------------

    @Test
    fun `listAccounts concatenates two pages via nextOffset`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            val offset = request.url.parameters["offset"]
            val body = if (offset == "0" || offset == null) {
                """{"data":[{"id":"acc-synthetic-1","name":"Wallet"}],"nextOffset":1}"""
            } else {
                """{"data":[{"id":"acc-synthetic-2","name":"Cash"}]}"""
            }
            jsonResponse(body, HttpStatusCode.OK)
        }
        val client = clientWith(engine)

        val result = client.listAccounts()

        assertTrue(result is ApiResult.Success)
        val accounts = (result as ApiResult.Success).data
        assertEquals(listOf("acc-synthetic-1", "acc-synthetic-2"), accounts.map { it.id })
        assertEquals(2, requestCount)
    }

    // ---- Pagination against the real response shape ------------------------

    @Test
    fun `listCategories follows limit-offset pages when the server sends no nextOffset`() = runTest {
        // The live API returns {categories, limit, offset, total} and never a nextOffset, so a
        // loop keyed on that field alone stopped after page one and dropped the rest.
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            val offset = request.url.parameters["offset"]?.toInt() ?: 0
            val body = when (offset) {
                0 -> """{"categories":[${(1..200).joinToString(",") { """{"id":"c$it"}""" }}],"limit":200,"offset":0,"total":250}"""
                else -> """{"categories":[${(201..250).joinToString(",") { """{"id":"c$it"}""" }}],"limit":200,"offset":200,"total":250}"""
            }
            jsonResponse(body, HttpStatusCode.OK)
        }

        val result = clientWith(engine).listCategories()

        assertTrue(result is ApiResult.Success)
        assertEquals(250, (result as ApiResult.Success).data.size)
        assertEquals(2, calls)
    }

    @Test
    fun `listAccounts stops on a short page without asking for another`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            jsonResponse("""{"accounts":[{"id":"a1"},{"id":"a2"}],"limit":200,"offset":0}""", HttpStatusCode.OK)
        }

        val result = clientWith(engine).listAccounts()

        assertTrue(result is ApiResult.Success)
        assertEquals(2, (result as ApiResult.Success).data.size)
        // A page shorter than the limit is the last one; a second request would be wasted budget.
        assertEquals(1, calls)
    }

    @Test
    fun `findRecords reads the records array even when another array precedes it`() = runTest {
        // Verbatim shape from the live API: appliedRecordDateFilters is an array of filter
        // strings and appears before "records". Taking the first array found parsed those
        // strings as records, which broke reconciliation - the check that stops duplicates.
        val body = """
            {"appliedRecordDateFilters":["gte.2026-09-02T00:00:00.000Z","lt.2026-09-03T00:00:00.000Z"],
             "limit":50,"offset":0,
             "records":[{"id":"rec-1","accountId":"acc-1","amount":{"value":-1,"currencyCode":"BDT"},
                         "recordDate":"2026-09-02T17:06:55.000Z"}]}
        """.trimIndent()
        val engine = MockEngine { jsonResponse(body, HttpStatusCode.OK) }

        val result = clientWith(engine).findRecords("acc-1", "2026-09-02", "-1")

        assertTrue(result is ApiResult.Success)
        val records = (result as ApiResult.Success).data
        assertEquals(1, records.size)
        assertEquals("rec-1", records.single().id)
    }

    // ---- Real server payloads ---------------------------------------------

    @Test
    fun `createRecords parses a genuine 200 response from the live API`() = runTest {
        // Captured verbatim from a real POST /records against rest.budgetbakers.com, with the
        // account/record UUIDs replaced by synthetic ones. Pins the DTOs to the shape the
        // server actually sends - note `category` arrives as a nested object, not a
        // `categoryId` string, and extra fields (recordType, labels, accountName, source)
        // must be tolerated.
        val body = """
            {"summary":{"total":1,"succeeded":1,"clientErrors":0,"serverErrors":0,"documentsWritten":1},
             "results":[{"inputIndex":0,"id":"00000000-0000-4000-8000-000000000001","success":true,
               "record":{"id":"00000000-0000-4000-8000-000000000001",
                 "accountId":"00000000-0000-4000-8000-000000000000",
                 "note":"SMS2Wallet test","counterParty":"SMS2Wallet test",
                 "amount":{"value":-1,"currencyCode":"BDT"},
                 "recordDate":"2026-09-02T17:06:55.000Z",
                 "category":{"id":"00000000-0000-4000-8000-000000000002","name":"Unknown expense",
                   "group":{"id":"unknown_records","name":"Unknown"},"color":"#d0d0d0"},
                 "recordState":"cleared","recordType":"expense","labels":[],
                 "createdAt":"2026-09-02T17:06:58.435Z","updatedAt":"2026-09-02T17:06:59.355Z",
                 "accountName":"Cash","accountIsBankSync":false,"transfer":null,"source":"rest"}}]}
        """.trimIndent()
        val engine = MockEngine { jsonResponse(body, HttpStatusCode.OK) }

        val result = clientWith(engine).createRecords(listOf(sampleRequest()))

        assertTrue(result is ApiResult.Success)
        val response = (result as ApiResult.Success).data
        assertTrue(response.allSucceeded)
        assertEquals(1, response.summary.succeeded)
        val created = response.results.single()
        assertEquals(0, created.inputIndex)
        // The id is what gets stored as wallet_record_id and makes the push terminal.
        assertEquals("00000000-0000-4000-8000-000000000001", created.id)
        assertEquals(-1.0, created.record!!.amount.value, 0.001)
    }
}
