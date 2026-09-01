package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecordsSummaryDto(
    val total: Int = 0,
    val succeeded: Int = 0,
    val clientErrors: Int = 0,
    val serverErrors: Int = 0,
    val documentsWritten: Int = 0,
)

/**
 * One item's outcome within a [CreateRecordsResponse], positionally keyed to
 * the request list by [inputIndex] (the array indices are NOT guaranteed to
 * come back in the same order they were sent, so [inputIndex] — not list
 * position in [CreateRecordsResponse.results] — is the only safe join key).
 */
@Serializable
data class RecordResultDto(
    val inputIndex: Int,
    val success: Boolean,
    val id: String? = null,
    val record: RecordDto? = null,
    val error: String? = null,
    /** `"client_error"` or `"server_error"`. */
    val errorType: String? = null,
    val fields: List<String>? = null,
)

/**
 * Response body of `POST /records`.
 *
 * CRITICAL: an HTTP 200/207 response here does NOT mean every item
 * succeeded — batch writes are not atomic. HTTP 207 explicitly signals a
 * mixed result, but even a 200 can carry per-item failures in [results].
 * Always inspect [results] (or use [allSucceeded] / [failuresByInputIndex])
 * instead of branching on the HTTP status code alone.
 */
@Serializable
data class CreateRecordsResponse(
    val summary: RecordsSummaryDto = RecordsSummaryDto(),
    val results: List<RecordResultDto> = emptyList(),
) {
    /** True only when every item in [results] reports success. Never infer this from the HTTP status alone. */
    val allSucceeded: Boolean
        get() = results.isNotEmpty() && results.all { it.success }

    fun successesByInputIndex(): Map<Int, RecordResultDto> =
        results.filter { it.success }.associateBy { it.inputIndex }

    fun failuresByInputIndex(): Map<Int, RecordResultDto> =
        results.filter { !it.success }.associateBy { it.inputIndex }
}
