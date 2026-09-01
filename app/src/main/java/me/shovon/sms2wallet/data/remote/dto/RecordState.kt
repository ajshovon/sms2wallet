package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reconciliation state of a record, shared by request and response shapes. */
@Serializable
enum class RecordState {
    @SerialName("reconciled") RECONCILED,
    @SerialName("cleared") CLEARED,
    @SerialName("uncleared") UNCLEARED,
}
