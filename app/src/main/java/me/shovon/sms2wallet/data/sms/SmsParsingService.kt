package me.shovon.sms2wallet.data.sms

import me.shovon.bdparser.bank.BankParser
import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.domain.model.SenderOverride
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
    fun parse(
        enabledParsers: List<BankParser>,
        raw: RawSms,
        senderOverrides: Map<String, String> = emptyMap(),
    ): IngestResult {
        // A taught sender is an explicit decision and outranks the promotional heuristic: an
        // operator-suffixed ID like "GP-MTBL-P" reads as promotional but may be exactly the
        // sender the user pointed at their bank.
        val isTaught = senderOverrides.containsKey(SenderOverride.normalise(raw.sender))
        if (!isTaught &&
            isPromotionalSender(raw.sender) &&
            !BankParserFactory.isKnownBankSender(raw.sender, raw.body)
        ) {
            return IngestResult.Ignored("Promotional sender '${raw.sender}'")
        }

        val candidates = matchingParsers(enabledParsers, raw.sender, raw.body, senderOverrides)
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

    /**
     * Picks the parsers to try, best evidence first.
     *
     * The caller tries each in turn until one parses, so this is an ordered list of candidates
     * rather than a single winner.
     *
     * A taught sender is a *default* for that number, not an exclusive claim. Mobile Number
     * Portability can put several banks behind one number - a ported line carrying both MTB and
     * EBL is normal - so treating the teaching as exclusive would take a bank that already
     * worked and break it. Within a taught sender the order is:
     *
     *  1. a parser whose marker is in the body, which is positive evidence for that specific
     *     bank and beats a default;
     *  2. the taught provider, which is the answer for the messages that identify nobody -
     *     exactly why the user had to teach it;
     *  3. anything else claiming the sender, so the teaching can also correct a wrong match.
     *
     * With no teaching, dispatch is unchanged: sender pattern first, body markers second.
     */
    private fun matchingParsers(
        pool: List<BankParser>,
        sender: String,
        body: String,
        senderOverrides: Map<String, String>,
    ): List<BankParser> {
        val taught = senderOverrides[SenderOverride.normalise(sender)]
            ?.let { name -> pool.filter { it.getBankName() == name } }
            .orEmpty()

        if (taught.isEmpty()) {
            val bySender = pool.filter { it.canHandle(sender) }
            if (bySender.isNotEmpty()) return bySender
            return pool.filter { it.canHandleMessage(sender, body) }
        }

        // Body evidence only: canHandleMessage also returns true on a sender match, which would
        // let the sender pattern outrank the teaching meant to override it.
        val byBodyMarker = pool.filter { !it.canHandle(sender) && it.canHandleMessage(sender, body) }

        return (byBodyMarker + taught + pool.filter { it.canHandle(sender) }).distinct()
    }

    private fun isPromotionalSender(sender: String): Boolean {
        val trimmed = sender.trim().uppercase()
        return trimmed.endsWith("-P") || trimmed.endsWith("-G")
    }
}
