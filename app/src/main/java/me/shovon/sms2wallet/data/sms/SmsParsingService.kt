package me.shovon.sms2wallet.data.sms

import me.shovon.bdparser.bank.BankParser
import me.shovon.bdparser.bank.BankParserFactory
import javax.inject.Inject

/**
 * Runs a single [RawSms] through the bank-parser catalogue and classifies the outcome as an
 * [IngestResult].
 *
 * Callers pass in the *enabled* parser pool (typically
 * [me.shovon.sms2wallet.data.prefs.AppPreferences.enabledParsers]) rather than this service
 * reaching into preferences itself, so it stays a small, easily-testable pure function of its
 * inputs.
 */
class SmsParsingService @Inject constructor() {

    /**
     * Classifies [raw] against [enabledParsers].
     *
     * Dispatch mirrors [BankParserFactory.getParsers] (sender match wins outright; body-marker
     * fallback via [BankParser.canHandleMessage] only when no parser matches by sender) but is
     * scoped to [enabledParsers] so a user-disabled bank is never parsed even if its parser
     * would otherwise recognise the message.
     *
     * A sender with a DLT promotional/group suffix (`-P`/`-G`) is ignored outright unless the
     * *full, unrestricted* parser catalogue ([BankParserFactory.isKnownBankSender]) recognises
     * it as a real bank sender - Mobile Number Portability can rewrite a bank's sender ID onto
     * a promotional-looking route, so a known-bank match always overrides the heuristic.
     */
    fun parse(enabledParsers: List<BankParser>, raw: RawSms): IngestResult {
        if (isPromotionalSender(raw.sender) &&
            !BankParserFactory.isKnownBankSender(raw.sender, raw.body)
        ) {
            return IngestResult.Ignored("Promotional sender '${raw.sender}'")
        }

        val candidates = matchingParsers(enabledParsers, raw.sender, raw.body)
        if (candidates.isEmpty()) {
            return if (BankParserFactory.isKnownBankSender(raw.sender, raw.body)) {
                IngestResult.Ignored("Sender is a known bank but its parser is disabled")
            } else {
                IngestResult.Unmatched("No enabled parser recognises sender '${raw.sender}'")
            }
        }

        val parsed = candidates.firstNotNullOfOrNull { it.parse(raw.body, raw.sender, raw.timestamp) }
        return parsed?.let { IngestResult.Parsed(it) }
            ?: IngestResult.Unmatched(
                "Sender '${raw.sender}' matched ${candidates.first().getBankName()} but the message body did not parse as a transaction"
            )
    }

    private fun matchingParsers(pool: List<BankParser>, sender: String, body: String): List<BankParser> {
        val bySender = pool.filter { it.canHandle(sender) }
        if (bySender.isNotEmpty()) return bySender
        return pool.filter { it.canHandleMessage(sender, body) }
    }

    private fun isPromotionalSender(sender: String): Boolean {
        val trimmed = sender.trim().uppercase()
        return trimmed.endsWith("-P") || trimmed.endsWith("-G")
    }
}
