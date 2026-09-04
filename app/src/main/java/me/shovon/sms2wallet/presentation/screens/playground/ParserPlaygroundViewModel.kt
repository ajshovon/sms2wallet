package me.shovon.sms2wallet.presentation.screens.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.presentation.model.ExtractedFieldUiState
import me.shovon.sms2wallet.presentation.model.ParserMatchResultUiState
import me.shovon.sms2wallet.presentation.model.ParserPlaygroundUiState
import me.shovon.sms2wallet.presentation.util.MoneyFormatter

import androidx.lifecycle.SavedStateHandle

/**
 * Parser playground: runs every registered parser against a pasted SMS and reports what each
 * one extracted.
 *
 * Parsing runs on [Dispatchers.Default] rather than inline in the click handler - every parser
 * in the catalogue is evaluated against the body, which is enough regex work to be visible as a
 * frame drop on the main thread.
 */
@HiltViewModel
class ParserPlaygroundViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialSender = savedStateHandle.get<String>("sender").orEmpty()
    private val initialBody = savedStateHandle.get<String>("body").orEmpty()

    private val _uiState = MutableStateFlow(
        ParserPlaygroundUiState(
            senderInput = initialSender,
            bodyInput = initialBody
        )
    )
    val uiState: StateFlow<ParserPlaygroundUiState> = _uiState.asStateFlow()

    init {
        if (initialBody.isNotBlank()) {
            run()
        }
    }

    fun onSenderChange(value: String) { _uiState.value = _uiState.value.copy(senderInput = value) }
    fun onBodyChange(value: String) { _uiState.value = _uiState.value.copy(bodyInput = value) }

    fun run() {
        val state = _uiState.value
        if (state.bodyInput.isBlank()) return
        viewModelScope.launch {
            _uiState.value = state.copy(isRunning = true)
            val results = withContext(Dispatchers.Default) {
                runAllParsers(state.senderInput, state.bodyInput)
            }
            _uiState.value = _uiState.value.copy(results = results, hasRun = true, isRunning = false)
        }
    }

    /** Runs every registered parser against [sender]/[body] and reports match + extracted fields. */
    private fun runAllParsers(sender: String, body: String): List<ParserMatchResultUiState> {
        val timestamp = System.currentTimeMillis()
        return BankParserFactory.getAllParsers().map { parser ->
            val name = parser.getBankName()
            if (!parser.canHandleMessage(sender, body)) {
                return@map ParserMatchResultUiState(
                    providerName = name,
                    matched = false,
                    failureReason = "Sender/body did not match this provider's patterns",
                )
            }
            val parsed = runCatching { parser.parse(body, sender, timestamp) }.getOrNull()
                ?: return@map ParserMatchResultUiState(
                    providerName = name,
                    matched = false,
                    failureReason = "Provider recognised, but no transaction could be extracted " +
                        "(may not be a transaction alert)",
                )
            ParserMatchResultUiState(
                providerName = name,
                matched = true,
                extractedFields = buildList {
                    add(ExtractedFieldUiState("Amount", MoneyFormatter.formatBdt(parsed.amount)))
                    add(ExtractedFieldUiState("Type", parsed.type.name))
                    parsed.merchant?.let { add(ExtractedFieldUiState("Merchant", it)) }
                    parsed.accountLast4?.let { add(ExtractedFieldUiState("Account last 4", it)) }
                    parsed.balance?.let { add(ExtractedFieldUiState("Balance", MoneyFormatter.formatBdt(it))) }
                    parsed.reference?.let { add(ExtractedFieldUiState("Reference", it)) }
                    add(ExtractedFieldUiState("Currency", parsed.currency))
                },
            )
        }
    }
}
