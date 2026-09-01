package me.shovon.sms2wallet.data.sms

/**
 * Receives the outcome of parsing one [RawSms]. This is the seam between the SMS ingest layer
 * and persistence: this module defines and binds only a no-op implementation
 * ([NoOpIngestSink]); a later pass supplies a Room-backed [IngestSink] that de-duplicates via
 * [me.shovon.bdparser.ParsedTransaction.generateTransactionId] and writes `Parsed` results to
 * the database.
 */
interface IngestSink {
    suspend fun accept(result: IngestResult, raw: RawSms)
}

/**
 * Default binding so the app compiles and runs end-to-end (SMS in, nowhere to go yet) before
 * the persistence layer is wired in. Intentionally does nothing - in particular, it must never
 * log [RawSms.body]/[RawSms.sender], since SMS content is user PII.
 */
class NoOpIngestSink : IngestSink {
    override suspend fun accept(result: IngestResult, raw: RawSms) {
        // Intentionally no-op until a persistence-backed IngestSink is bound in its place.
    }
}
