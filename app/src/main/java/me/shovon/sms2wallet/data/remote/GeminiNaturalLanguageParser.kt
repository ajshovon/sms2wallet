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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.shovon.sms2wallet.domain.nlp.CategoryPrompt
import me.shovon.sms2wallet.domain.nlp.NlPrompt
import me.shovon.sms2wallet.domain.nlp.ParsedNlTransaction

private const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

/**
 * [NaturalLanguageParser] backed by the Google Gemini REST API.
 *
 * Two deliberate choices about correctness:
 *
 * - The response is constrained by a JSON **schema**, not coaxed by prose. Category and account
 *   are declared as enums built from the user's own names, so a hallucinated category is not
 *   something to detect and reject afterwards - it cannot come back in the first place.
 * - `temperature = 0`. This is an extraction task with one right answer; sampling variety would
 *   only mean the same phrase parses differently on two tries.
 *
 * The API key is read lazily per call via [apiKeyProvider] because the user can change it in
 * settings at any time, and it is never logged - see [RedactingLogger].
 */
class GeminiNaturalLanguageParser(
    engine: HttpClientEngine,
    private val apiKeyProvider: suspend () -> String?,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : NaturalLanguageParser {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(engine) {
        expectSuccess = false

        install(ContentNegotiation) { json(json) }

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

    override suspend fun parse(
        input: String,
        categoryNames: List<String>,
        accountNames: List<String>,
        model: String,
    ): NlParseResult {
        if (input.isBlank()) return NlParseResult.EmptyResult
        val apiKey = apiKeyProvider() ?: return NlParseResult.NotConfigured

        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(
                        buildJsonObject {
                            put(
                                "text",
                                NlPrompt.systemInstruction(
                                    categoryNames = categoryNames,
                                    accountNames = accountNames,
                                    now = now(),
                                ),
                            )
                        }
                    )
                }
            }
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", input) })
                        }
                    }
                )
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0)
                put("responseSchema", responseSchema(categoryNames, accountNames))
            }
        }

        val response = try {
            httpClient.post("$baseUrl/models/$model:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            return NlParseResult.NetworkError("The request timed out.")
        } catch (e: SocketTimeoutException) {
            return NlParseResult.NetworkError("The request timed out.")
        } catch (e: ConnectTimeoutException) {
            return NlParseResult.NetworkError("Could not reach Google.")
        } catch (e: IOException) {
            return NlParseResult.NetworkError(e.message)
        }

        failureFor(response)?.let { return it }

        val text = try {
            candidateText(response.bodyAsText())
        } catch (e: SerializationException) {
            return NlParseResult.EmptyResult
        } ?: return NlParseResult.EmptyResult

        val transaction = try {
            readTransaction(text)
        } catch (e: SerializationException) {
            return NlParseResult.EmptyResult
        } catch (e: IllegalArgumentException) {
            // A non-numeric amount, or a malformed payload that still parsed as JSON.
            return NlParseResult.EmptyResult
        }

        return transaction?.let { NlParseResult.Success(it) } ?: NlParseResult.EmptyResult
    }

    override suspend fun classify(
        subjects: List<CategoryPrompt.Subject>,
        categoryLabels: List<String>,
        model: String,
    ): CategorySuggestionResult {
        // Nothing to choose from means nothing to ask: without categories the schema would have
        // no enum and the model would be free to invent labels.
        if (subjects.isEmpty() || categoryLabels.isEmpty()) {
            return CategorySuggestionResult.Success(emptyMap())
        }
        val apiKey = apiKeyProvider() ?: return CategorySuggestionResult.NotConfigured

        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(
                        buildJsonObject {
                            put("text", CategoryPrompt.systemInstruction(categoryLabels))
                        }
                    )
                }
            }
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", CategoryPrompt.userContent(subjects)) })
                        }
                    }
                )
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0)
                put("responseSchema", classificationSchema(categoryLabels))
            }
        }

        val response = try {
            httpClient.post("$baseUrl/models/$model:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            return CategorySuggestionResult.NetworkError("The request timed out.")
        } catch (e: SocketTimeoutException) {
            return CategorySuggestionResult.NetworkError("The request timed out.")
        } catch (e: ConnectTimeoutException) {
            return CategorySuggestionResult.NetworkError("Could not reach Google.")
        } catch (e: IOException) {
            return CategorySuggestionResult.NetworkError(e.message)
        }

        when (val failure = failureFor(response)) {
            null -> Unit
            NlParseResult.InvalidApiKey -> return CategorySuggestionResult.InvalidApiKey
            is NlParseResult.HttpError ->
                return CategorySuggestionResult.HttpError(failure.status, failure.message)
            else -> return CategorySuggestionResult.NetworkError(null)
        }

        val text = try {
            candidateText(response.bodyAsText())
        } catch (e: SerializationException) {
            null
        } ?: return CategorySuggestionResult.Success(emptyMap())

        return CategorySuggestionResult.Success(readAssignments(text, categoryLabels))
    }

    /**
     * Reads the merchant/category pairs, dropping anything that does not name a real category.
     *
     * The enum should make that impossible, but this is the boundary where a model's output
     * becomes a category id, and a label that does not exist would resolve to nothing anyway.
     */
    private fun readAssignments(payload: String, categoryLabels: List<String>): Map<String, String> {
        val array = runCatching {
            json.parseToJsonElement(payload).jsonObject["assignments"]?.jsonArray
        }.getOrNull() ?: return emptyMap()

        val allowed = categoryLabels.toSet()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val merchant = obj.string("merchant")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val category = obj.string("category")?.trim()?.takeIf { it in allowed } ?: return@mapNotNull null
            merchant to category
        }.toMap()
    }

    private fun classificationSchema(categoryLabels: List<String>): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("assignments") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("merchant") { put("type", "STRING") }
                        putJsonObject("category") {
                            put("type", "STRING")
                            put("enum", categoryLabels.toJsonArray())
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("merchant"))
                        add(JsonPrimitive("category"))
                    }
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("assignments")) }
    }

    override suspend fun verify(model: String): String? {
        val apiKey = apiKeyProvider() ?: return "Enter an API key first."

        // GET the model rather than generating: it proves the key works and that this account
        // can reach this model, without spending a generation or sending any user data.
        val response = try {
            httpClient.get("$baseUrl/models/$model") {
                header("x-goog-api-key", apiKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            return "The request timed out."
        } catch (e: SocketTimeoutException) {
            return "The request timed out."
        } catch (e: ConnectTimeoutException) {
            return "Could not reach Google."
        } catch (e: IOException) {
            return e.message ?: "Could not reach Google."
        }

        return when (val failure = failureFor(response)) {
            null -> null
            NlParseResult.InvalidApiKey -> "The API key was rejected, or it cannot use this model."
            is NlParseResult.HttpError -> failure.message ?: "Google returned HTTP ${failure.status}."
            else -> "Could not verify the key."
        }
    }

    // ---- Response handling -------------------------------------------------------

    /** Maps a non-2xx response to a result, or null when the response was a success. */
    private suspend fun failureFor(response: HttpResponse): NlParseResult? {
        if (response.status.value in 200..299) return null
        return when (response.status.value) {
            // Google reports a bad, revoked or unauthorised key as 400 or 403 rather than 401.
            400, 401, 403 -> NlParseResult.InvalidApiKey
            else -> NlParseResult.HttpError(response.status.value, errorMessage(response.bodyAsText()))
        }
    }

    /** Pulls `error.message` out of Google's error envelope, ignoring anything unexpected. */
    private fun errorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()

    /**
     * Extracts the model's JSON payload from the response envelope.
     *
     * Returns null rather than throwing when a candidate is absent - a prompt filtered for
     * safety, or a `MAX_TOKENS` stop, both produce a well-formed response with nothing in it.
     */
    private fun candidateText(body: String): String? {
        val candidate = json.parseToJsonElement(body)
            .jsonObject["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: return null

        return candidate["content"]
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
    }

    private fun readTransaction(payload: String): ParsedNlTransaction? {
        val obj = json.parseToJsonElement(payload) as? JsonObject ?: return null

        val title = obj.string("title")?.trim().orEmpty()
        // Read the amount as text, not as a Double: BigDecimal("12.34") is exact where
        // BigDecimal(12.34) is not, and this value ends up in a financial record.
        val rawAmount = obj["amount"]?.jsonPrimitive?.content ?: return null
        val amount = rawAmount.toBigDecimalOrNull() ?: return null

        // Nothing to show the user: no merchant and no money.
        if (title.isEmpty() && amount.signum() == 0) return null

        return ParsedNlTransaction(
            // The sign carries the direction; the form works in magnitudes.
            amount = amount.abs(),
            isIncome = amount.signum() > 0,
            title = title,
            note = obj.string("note")?.trim()?.takeIf { it.isNotEmpty() },
            categoryName = obj.string("category")?.trim()?.takeIf { it.isNotEmpty() },
            accountName = obj.string("account")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()

    // ---- Schema ------------------------------------------------------------------

    /**
     * The structured-output schema.
     *
     * `category`/`account` are only present when the user shares those names, and when present
     * they are enums over exactly those names. The model therefore cannot return a category the
     * user does not have, and cannot return an account at all when accounts are withheld.
     */
    private fun responseSchema(categoryNames: List<String>, accountNames: List<String>): JsonObject =
        buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("amount") { put("type", "NUMBER") }
                putJsonObject("title") { put("type", "STRING") }
                putJsonObject("note") { put("type", "STRING") }
                if (categoryNames.isNotEmpty()) {
                    putJsonObject("category") {
                        put("type", "STRING")
                        put("enum", categoryNames.toJsonArray())
                    }
                }
                if (accountNames.isNotEmpty()) {
                    putJsonObject("account") {
                        put("type", "STRING")
                        put("enum", accountNames.toJsonArray())
                    }
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("amount"))
                add(JsonPrimitive("title"))
            }
        }

    private fun List<String>.toJsonArray(): JsonArray = buildJsonArray {
        forEach { add(JsonPrimitive(it)) }
    }
}
