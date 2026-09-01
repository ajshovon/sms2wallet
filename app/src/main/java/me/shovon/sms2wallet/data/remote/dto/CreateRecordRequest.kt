package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Body item for `POST /records` (sent as a JSON array of 1..50 of these —
 * see `WalletApiClient.createRecords`).
 *
 * Validation performed here is intentionally the cheap, purely local kind
 * (non-blank required fields, string length caps, non-zero amount) that the
 * server would reject anyway — failing fast avoids burning a request against
 * the 300/hour rate limit on something that was never going to succeed.
 */
@Serializable
data class CreateRecordRequest(
    val accountId: String,
    val amount: RecordAmount,
    /** ISO-8601 date-time. Rejected by the server if more than 24h in the future. */
    val recordDate: String,
    val categoryId: String? = null,
    val counterParty: String? = null,
    val note: String? = null,
    val labelIds: List<String>? = null,
    val recordState: RecordState = RecordState.CLEARED,
    /**
     * Transfer linkage payload. Not modeled yet — kept as an opaque, nullable
     * JSON element so a caller can pass one through untouched once transfer
     * support is designed, without this client guessing at fields that
     * aren't in scope.
     */
    val transfer: JsonElement? = null,
) {
    init {
        require(accountId.isNotBlank()) { "accountId is required" }
        require(recordDate.isNotBlank()) { "recordDate is required" }
        require(amount.value != 0.0) {
            "amount.value must be non-zero (negative = expense, positive = income)"
        }
        counterParty?.let { require(it.length <= 255) { "counterParty must be <= 255 characters" } }
        note?.let { require(it.length <= 255) { "note must be <= 255 characters" } }
    }
}
