package me.shovon.sms2wallet.sms

import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.data.sms.IngestResult
import me.shovon.sms2wallet.data.sms.RawSms
import me.shovon.sms2wallet.data.sms.SmsParsingService
import me.shovon.sms2wallet.domain.model.SenderOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch when Mobile Number Portability has stripped a bank's masked sender ID.
 *
 * The parsers already fall back to searching the body for a brand token, but that only fires
 * when the message carries one. MTB's plain balance alerts do not, so they go unmatched however
 * good the parser is - which is what a taught sender fixes.
 *
 * Bodies below are the redacted placeholders from the library's own parser tests.
 */
class SenderOverrideDispatchTest {

    private val service = SmsParsingService()
    private val parsers = BankParserFactory.getAllParsers()

    /** An MTB debit alert with no "MTB" anywhere in it - the case that fails today. */
    private val mtbBodyWithoutBrandToken =
        "Dear Customer, Your A/C XXXXX000000 has been Debited by BDT 15,000.00 on 01/01/24. " +
            "Available balance BDT 1,00,000.00."

    private fun parse(sender: String, body: String, overrides: Map<String, String> = emptyMap()) =
        service.parse(parsers, RawSms(id = 0, sender = sender, body = body, timestamp = 0L), overrides)

    @Test
    fun `an MNP-rewritten sender is unmatched when the body carries no brand token`() {
        val result = parse("01712345678", mtbBodyWithoutBrandToken)

        assertTrue("expected Unmatched, got $result", result is IngestResult.Unmatched)
    }

    @Test
    fun `teaching the sender routes it to the right provider`() {
        val result = parse(
            sender = "01712345678",
            body = mtbBodyWithoutBrandToken,
            overrides = mapOf("01712345678" to "Mutual Trust Bank"),
        )

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
        assertEquals("Mutual Trust Bank", (result as IngestResult.Parsed).transaction.bankName)
    }

    @Test
    fun `sender matching is case and whitespace insensitive`() {
        val overrides = mapOf(SenderOverride.normalise(" mtb-portable ") to "Mutual Trust Bank")

        val result = parse(" MTB-Portable ", mtbBodyWithoutBrandToken, overrides)

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
    }

    @Test
    fun `a taught sender overrides a parser that would otherwise claim it`() {
        // "MTB" is claimed by MutualTrustBankParser, but the user has pointed it at bKash.
        val bkashBody = "Payment Tk 1,250.00 to SHWAPNO successful. Fee Tk 0.00. " +
            "Balance Tk 8,000.00. TrxID AB12CD34EF at 01/01/2024 12:00"

        val result = parse("MTB", bkashBody, mapOf("MTB" to "bKash"))

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
        assertEquals("bKash", (result as IngestResult.Parsed).transaction.bankName)
    }

    @Test
    fun `a taught sender is not discarded as promotional`() {
        // Operator-suffixed IDs read as promotional, but may be exactly what the bank now uses.
        val result = parse(
            sender = "GP-MTBL-P",
            body = mtbBodyWithoutBrandToken,
            overrides = mapOf("GP-MTBL-P" to "Mutual Trust Bank"),
        )

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
    }

    @Test
    fun `teaching a sender does not affect any other sender`() {
        val overrides = mapOf("01712345678" to "Mutual Trust Bank")

        val untouched = parse("01799999999", mtbBodyWithoutBrandToken, overrides)

        assertTrue("expected Unmatched, got $untouched", untouched is IngestResult.Unmatched)
    }

    /** EBL's own sample body: it carries an "EBL" marker, unlike MTB's plain alerts. */
    private val eblBody =
        "AC 12345**6789 is debited with BDT 3,000.00 as EBL Account Transfer on 02-Jan-24 09:00:00 AM BST"

    @Test
    fun `a shared MNP number still parses the other bank it also carries`() {
        // One ported number delivers both MTB and EBL. Teaching it as MTB must not stop EBL's
        // own messages - which do identify themselves - from being parsed.
        val overrides = mapOf("01712345678" to "Mutual Trust Bank")

        val result = parse("01712345678", eblBody, overrides)

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
        assertEquals("Eastern Bank", (result as IngestResult.Parsed).transaction.bankName)
    }

    @Test
    fun `the taught provider still wins when the body identifies nobody`() {
        val overrides = mapOf("01712345678" to "Mutual Trust Bank")

        val result = parse("01712345678", mtbBodyWithoutBrandToken, overrides)

        assertEquals("Mutual Trust Bank", (result as IngestResult.Parsed).transaction.bankName)
    }

    @Test
    fun `teaching a sender does not regress a bank that already worked from it`() {
        // Before any teaching, EBL parses from a numeric sender via its body marker.
        val before = parse("01712345678", eblBody)

        assertTrue("expected Parsed, got $before", before is IngestResult.Parsed)
        assertEquals("Eastern Bank", (before as IngestResult.Parsed).transaction.bankName)
    }
}
