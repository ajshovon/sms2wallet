package me.shovon.sms2wallet.data.sms

import me.shovon.bdparser.ParsedTransaction

/**
 * Outcome of running one [RawSms] through [SmsParsingService]. Consumed by [IngestSink]
 * implementations (a later pass wires a Room-backed sink that persists [Parsed] results and,
 * optionally, logs [Unmatched]/[Ignored] ones for diagnostics).
 */
sealed interface IngestResult {

    /** The message matched a bank parser and was successfully decoded into a transaction. */
    data class Parsed(val transaction: ParsedTransaction) : IngestResult

    /**
     * The message's sender matched a known bank/parser, but no registered parser could
     * extract a transaction from the body (e.g. an unsupported message shape, or a
     * non-transaction notice the parser doesn't otherwise recognise as ignorable).
     */
    data class Unmatched(val reason: String) : IngestResult

    /**
     * The message was deliberately not parsed - e.g. a promotional sender, or a sender that
     * matched a parser the user has disabled. Distinct from [Unmatched] so a UI can surface
     * "nothing to do here" differently from "this looked like a bank SMS but we couldn't read it".
     */
    data class Ignored(val reason: String) : IngestResult
}
