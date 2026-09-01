package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A record as returned by `GET /records`, or embedded in a create-records result. */
@Serializable
data class RecordDto(
    val id: String? = null,
    val accountId: String,
    val amount: RecordAmount,
    val recordDate: String,
    val categoryId: String? = null,
    val counterParty: String? = null,
    val note: String? = null,
    val labelIds: List<String>? = null,
    val recordState: RecordState? = null,
    val source: String? = null,
    val transfer: JsonElement? = null,
)
