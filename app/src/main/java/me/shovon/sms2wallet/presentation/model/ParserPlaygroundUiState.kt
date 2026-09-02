package me.shovon.sms2wallet.presentation.model

/**
 * UI state for the Parser playground: paste a raw SMS sender + body, see which of the
 * registered parsers match and every field each one extracts.
 */
data class ParserPlaygroundUiState(
    val senderInput: String = "",
    val bodyInput: String = "",
    val results: List<ParserMatchResultUiState> = emptyList(),
    val hasRun: Boolean = false,
    val isRunning: Boolean = false
)

/** Result of running one registered parser against the pasted SMS. */
data class ParserMatchResultUiState(
    val providerName: String,
    val matched: Boolean,
    val extractedFields: List<ExtractedFieldUiState> = emptyList(),
    val failureReason: String? = null
)

/** One field/value pair extracted by a matching parser (e.g. "Amount" -> "৳650.00"). */
data class ExtractedFieldUiState(
    val label: String,
    val value: String
)
