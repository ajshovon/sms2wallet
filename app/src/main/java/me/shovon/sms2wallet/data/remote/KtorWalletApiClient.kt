package me.shovon.sms2wallet.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shovon.sms2wallet.data.remote.dto.CreateRecordRequest
import me.shovon.sms2wallet.data.remote.dto.CreateRecordsResponse
import me.shovon.sms2wallet.data.remote.dto.RecordDto
import me.shovon.sms2wallet.data.remote.dto.UsageStatsDto
import me.shovon.sms2wallet.data.remote.dto.WalletAccountDto
import me.shovon.sms2wallet.data.remote.dto.WalletCategoryDto

private const val DEFAULT_BASE_URL = "https://rest.budgetbakers.com/wallet/v1/api"
private const val ACCOUNTS_PAGE_LIMIT = 200
private const val CATEGORIES_PAGE_LIMIT = 200
private const val RECORDS_SEARCH_LIMIT = 50
private const val MIN_CREATE_BATCH = 1
/** `POST /records` accepts `maxItems: 50` per the OpenAPI schema (`PATCH` is the one capped at 10). */
private const val MAX_CREATE_BATCH = 50

/**
 * [WalletApiClient] implementation on top of Ktor.
 *
 * @param engine injected so tests can pass `MockEngine`; production wiring
 *   uses the OkHttp engine provided by `NetworkModule`.
 * @param tokenProvider reads the bearer token lazily on every call (it may
 *   change between calls, e.g. if the user updates it in settings). Storage
 *   of the token is intentionally out of scope for this client.
 */
class KtorWalletApiClient(
    engine: HttpClientEngine,
    private val tokenProvider: suspend () -> String?,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WalletApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(engine) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }

        install(Logging) {
            level = LogLevel.HEADERS
            logger = RedactingLogger()
        }
    }

    /** Latest `X-RateLimit-*` snapshot observed from any response, used by [usageStats]. */
    @Volatile
    private var lastRateLimit: RateLimitInfo? = null

    // ---- WalletApiClient -----------------------------------------------

    override suspend fun listAccounts(): ApiResult<List<WalletAccountDto>> {
        val token = tokenProvider() ?: return ApiResult.Unauthorized
        return listAllPages("/accounts", ACCOUNTS_PAGE_LIMIT, token, WalletAccountDto.serializer(), "accounts")
    }

    override suspend fun listCategories(): ApiResult<List<WalletCategoryDto>> {
        val token = tokenProvider() ?: return ApiResult.Unauthorized
        return listAllPages("/categories", CATEGORIES_PAGE_LIMIT, token, WalletCategoryDto.serializer(), "categories")
    }

    override suspend fun createRecords(requests: List<CreateRecordRequest>): ApiResult<CreateRecordsResponse> {
        if (requests.size !in MIN_CREATE_BATCH..MAX_CREATE_BATCH) {
            return ApiResult.InvalidRequest(
                "createRecords accepts between $MIN_CREATE_BATCH and $MAX_CREATE_BATCH items, got ${requests.size}",
            )
        }
        val token = tokenProvider() ?: return ApiResult.Unauthorized

        val outcome = executeRaw {
            httpClient.post("$baseUrl/records") {
                applyAuth(token)
                contentType(ContentType.Application.Json)
                setBody(requests)
            }
        }

        return when (outcome) {
            is RawOutcome.Failure -> outcome.result
            is RawOutcome.Ok -> try {
                val parsed = json.decodeFromString(CreateRecordsResponse.serializer(), outcome.bodyText)
                ApiResult.Success(parsed, outcome.rateLimit)
            } catch (e: SerializationException) {
                ApiResult.HttpError(outcome.status.value, "Unparseable createRecords response: ${e.message}")
            }
        }
    }

    override suspend fun findRecords(
        accountId: String,
        dayIso: String,
        amount: String,
        source: String?,
    ): ApiResult<List<RecordDto>> {
        val token = tokenProvider() ?: return ApiResult.Unauthorized

        val outcome = executeRaw {
            httpClient.get("$baseUrl/records") {
                applyAuth(token)
                parameter("accountId", accountId)
                parameter("recordDate", "eq.$dayIso")
                parameter("amount", "eq.$amount")
                // `source` is not a documented filter; only send it if a caller explicitly asks,
                // so the default reconciliation query cannot be rejected for an unknown param.
                if (source != null) parameter("source", "eq.$source")
                parameter("limit", RECORDS_SEARCH_LIMIT)
            }
        }

        return when (outcome) {
            is RawOutcome.Failure -> outcome.result
            is RawOutcome.Ok -> try {
                ApiResult.Success(
                    parseListPayload(outcome.bodyText, RecordDto.serializer(), "records").items,
                    outcome.rateLimit,
                )
            } catch (e: SerializationException) {
                ApiResult.HttpError(outcome.status.value, "Unparseable findRecords response: ${e.message}")
            }
        }
    }

    override suspend fun validateToken(): ApiResult<Unit> {
        val token = tokenProvider() ?: return ApiResult.Unauthorized

        val outcome = executeRaw {
            httpClient.get("$baseUrl/accounts") {
                applyAuth(token)
                parameter("limit", 1)
            }
        }

        return when (outcome) {
            is RawOutcome.Failure -> outcome.result
            is RawOutcome.Ok -> ApiResult.Success(Unit, outcome.rateLimit)
        }
    }

    override suspend fun usageStats(): ApiResult<UsageStatsDto> {
        val info = lastRateLimit
        return ApiResult.Success(UsageStatsDto(limit = info?.limit, remaining = info?.remaining), info)
    }

    // ---- Pagination ------------------------------------------------------

    /**
     * Fetches every page of a paginated list endpoint by following
     * `nextOffset` until the server stops returning one (or returns an empty
     * page, as a defensive stop condition against a malformed/looping
     * `nextOffset`).
     */
    private suspend fun <T> listAllPages(
        path: String,
        pageLimit: Int,
        token: String,
        itemSerializer: KSerializer<T>,
        arrayKey: String,
    ): ApiResult<List<T>> {
        val accumulated = mutableListOf<T>()
        var offset = 0
        var latestRateLimit: RateLimitInfo? = null

        while (true) {
            val outcome = executeRaw {
                httpClient.get(baseUrl + path) {
                    applyAuth(token)
                    parameter("limit", pageLimit)
                    parameter("offset", offset)
                }
            }

            when (outcome) {
                is RawOutcome.Failure -> return outcome.result
                is RawOutcome.Ok -> {
                    latestRateLimit = outcome.rateLimit ?: latestRateLimit
                    val page = try {
                        parseListPayload(outcome.bodyText, itemSerializer, arrayKey)
                    } catch (e: SerializationException) {
                        return ApiResult.HttpError(outcome.status.value, "Unparseable list response: ${e.message}")
                    }
                    val items = page.items
                    val nextOffset = page.nextOffset
                    val total = page.total
                    accumulated += items
                    val next = nextPageOffset(
                        currentOffset = offset,
                        pageLimit = pageLimit,
                        received = items.size,
                        collected = accumulated.size,
                        serverNextOffset = nextOffset,
                        total = total,
                    ) ?: break
                    offset = next
                }
            }
        }

        return ApiResult.Success(accumulated, latestRateLimit)
    }

    /**
     * Extracts the item array and `nextOffset` from a paginated list
     * response.
     *
     * The exact JSON key wrapping the array (`"data"`, `"accounts"`,
     * `"records"`, ...) is not fixed by the API facts this client was built
     * from, so this deliberately does not hardcode one: it takes the first
     * top-level JSON array field in the response object, whatever it's
     * called. `nextOffset` / `total` are read by their known, fixed names.
     * This makes pagination robust to that naming detail without needing to
     * special-case each endpoint.
     */
    private fun <T> parseListPayload(
        bodyText: String,
        itemSerializer: KSerializer<T>,
        arrayKey: String,
    ): ListPage<T> {
        val root = json.parseToJsonElement(bodyText).jsonObject
        // Read the array by its documented name. "First top-level array" is NOT safe here:
        // GET /records answers with {appliedRecordDateFilters, limit, offset, records}, and
        // appliedRecordDateFilters is an array of filter strings that comes first - picking it
        // made every reconciliation lookup fail to parse, so a NEEDS_VERIFY row could never be
        // resolved. Falling back to the first array only when the named key is missing keeps
        // the old tolerance for a differently-wrapped payload.
        val arrayField = root[arrayKey] as? JsonArray
            ?: root.values.firstOrNull { it is JsonArray } as? JsonArray
            ?: JsonArray(emptyList())
        return ListPage(
            items = json.decodeFromJsonElement(ListSerializer(itemSerializer), arrayField),
            nextOffset = root["nextOffset"]?.jsonPrimitive?.intOrNull,
            total = root["total"]?.jsonPrimitive?.intOrNull,
        )
    }

    private data class ListPage<T>(val items: List<T>, val nextOffset: Int?, val total: Int?)

    /**
     * Offset of the next page, or null when this was the last one.
     *
     * The live API paginates with `limit`/`offset` (plus `total` when asked) and does **not**
     * return a `nextOffset`, so keying the loop off that field alone stopped after a single page
     * and silently dropped everything beyond it. This advances by the number of rows actually
     * received and stops on a short page, while still honouring `nextOffset` if the server ever
     * starts sending one.
     */
    private fun nextPageOffset(
        currentOffset: Int,
        pageLimit: Int,
        received: Int,
        collected: Int,
        serverNextOffset: Int?,
        total: Int?,
    ): Int? = when {
        received == 0 -> null
        serverNextOffset != null -> serverNextOffset
        // A page shorter than the limit is by definition the last one.
        received < pageLimit -> null
        total != null && collected >= total -> null
        else -> currentOffset + received
    }

    // ---- Raw request/response handling -----------------------------------

    private sealed class RawOutcome {
        data class Ok(val status: HttpStatusCode, val bodyText: String, val rateLimit: RateLimitInfo?) : RawOutcome()
        data class Failure(val result: ApiResult<Nothing>) : RawOutcome()
    }

    /**
     * Runs [block], classifying both transport-level exceptions and the
     * resulting HTTP status into an [ApiResult]-shaped outcome, and updates
     * [lastRateLimit] from the response headers whenever a response was
     * actually received (success or error status).
     *
     * Exception-to-[ApiResult.NetworkError.ambiguous] mapping, most specific
     * first:
     * - [HttpRequestTimeoutException] (Ktor's overall request timeout) — the
     *   request may have been fully sent and the server may be processing it
     *   when the client gives up waiting for a response: **ambiguous**. Note
     *   this type extends [CancellationException], so it must be caught
     *   before the generic cancellation passthrough below.
     * - [ConnectTimeoutException] — the TCP connection itself never
     *   completed, so nothing was sent: **not ambiguous**.
     * - [SocketTimeoutException] (Ktor) / [java.net.SocketTimeoutException]
     *   (JDK, in case the engine throws the raw one) — a read/write timeout
     *   after the connection was established: the request bytes may already
     *   be on the wire or the response may be mid-flight: **ambiguous**.
     * - [UnknownHostException] / [ConnectException] — DNS failure or
     *   connection refused: nothing was ever sent: **not ambiguous**.
     * - [CancellationException] (anything else, e.g. the calling coroutine
     *   scope was cancelled) — rethrown, never turned into a result.
     * - Generic [IOException] — an I/O failure of unknown origin mid-exchange
     *   (e.g. connection reset). Treated conservatively as **ambiguous**
     *   since we cannot rule out that the server received the request.
     * - Any other [Exception] — unexpected failure; treated conservatively
     *   as **ambiguous** for the same reason.
     */
    private suspend fun executeRaw(block: suspend () -> HttpResponse): RawOutcome {
        val response = try {
            block()
        } catch (e: HttpRequestTimeoutException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = true))
        } catch (e: ConnectTimeoutException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = false))
        } catch (e: SocketTimeoutException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = true))
        } catch (e: java.net.SocketTimeoutException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = true))
        } catch (e: UnknownHostException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = false))
        } catch (e: ConnectException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = true))
        } catch (e: Exception) {
            return RawOutcome.Failure(ApiResult.NetworkError(e.message, ambiguous = true))
        }

        val rateLimit = extractRateLimit(response)
        if (rateLimit != null) lastRateLimit = rateLimit

        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Headers arrived (we have a status code) but the body failed to
            // fully arrive. This is a definite server interaction, just an
            // incomplete one, so it's reported against the known status
            // rather than as an ambiguous transport failure.
            return RawOutcome.Failure(ApiResult.HttpError(response.status.value, e.message))
        }

        return when (response.status) {
            HttpStatusCode.Unauthorized -> RawOutcome.Failure(ApiResult.Unauthorized)
            HttpStatusCode.Conflict -> RawOutcome.Failure(classifyConflict(bodyText))
            HttpStatusCode.TooManyRequests -> RawOutcome.Failure(
                ApiResult.RateLimited(response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()),
            )
            else -> if (response.status.value in 200..299) {
                RawOutcome.Ok(response.status, bodyText, rateLimit)
            } else {
                RawOutcome.Failure(ApiResult.HttpError(response.status.value, bodyText.take(500)))
            }
        }
    }

    /** Body shape of a 409 `init_sync_in_progress` response. */
    @Serializable
    private data class SyncInProgressBody(
        val error: String? = null,
        val message: String? = null,
        @SerialName("retry_after_minutes") val retryAfterMinutes: Int? = null,
    )

    private fun classifyConflict(bodyText: String): ApiResult<Nothing> = try {
        val body = json.decodeFromString(SyncInProgressBody.serializer(), bodyText)
        if (body.error == "init_sync_in_progress") {
            ApiResult.SyncInProgress(body.retryAfterMinutes)
        } else {
            ApiResult.HttpError(HttpStatusCode.Conflict.value, body.message ?: bodyText.take(500))
        }
    } catch (e: SerializationException) {
        ApiResult.HttpError(HttpStatusCode.Conflict.value, bodyText.take(500))
    }

    private fun extractRateLimit(response: HttpResponse): RateLimitInfo? {
        val limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
        val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
        return if (limit == null && remaining == null) null else RateLimitInfo(limit, remaining)
    }

    private fun HttpRequestBuilder.applyAuth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}
