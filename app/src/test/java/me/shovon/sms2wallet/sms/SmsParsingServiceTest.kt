package me.shovon.sms2wallet.sms

import me.shovon.bdparser.TransactionType
import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.data.sms.IngestResult
import me.shovon.sms2wallet.data.sms.RawSms
import me.shovon.sms2wallet.data.sms.SmsParsingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Exercises [SmsParsingService] against real parser instances and synthetic (non-PII) BD SMS
 * bodies. All amounts, phone numbers, merchants and reference ids below are made up.
 */
class SmsParsingServiceTest {

    private val service = SmsParsingService()
    private val allParsers = BankParserFactory.getAllParsers()

    @Test
    fun `bKash payment SMS is parsed`() {
        val raw = RawSms(
            id = 1L,
            sender = "16247",
            body = "Payment of Tk 380.00 to SAMPLE SHOP BD is successful. Balance Tk 3,039.39. " +
                "TrxID ABC12XYZ99 at 01/09/2026 10:00",
            timestamp = 1_756_000_000_000L
        )

        val result = service.parse(allParsers, raw)

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
        val parsed = result as IngestResult.Parsed
        assertEquals(BigDecimal("380.00"), parsed.transaction.amount)
        assertEquals(TransactionType.EXPENSE, parsed.transaction.type)
        assertEquals("bKash", parsed.transaction.bankName)
        assertEquals("BDT", parsed.transaction.currency)
    }

    @Test
    fun `MTB card purchase SMS is parsed`() {
        val raw = RawSms(
            id = 2L,
            sender = "MTB",
            body = "Successful purchase transaction of BDT 500.00 from SAMPLE SHOP BD by MTB card- " +
                "XXXX1234 on 01-Sep-26 at 10:00. Current balance BDT 5,000.00. Helpline 16219",
            timestamp = 1_756_000_100_000L
        )

        val result = service.parse(allParsers, raw)

        assertTrue("expected Parsed, got $result", result is IngestResult.Parsed)
        val parsed = result as IngestResult.Parsed
        assertEquals(BigDecimal("500.00"), parsed.transaction.amount)
        assertEquals(TransactionType.EXPENSE, parsed.transaction.type)
        assertEquals("Mutual Trust Bank", parsed.transaction.bankName)
    }

    @Test
    fun `promotional sender with no known bank pattern is ignored`() {
        val raw = RawSms(
            id = 3L,
            sender = "AD-OFFERZONE-P",
            body = "Get 20% discount on your next purchase at SAMPLE SHOP BD! Limited time offer, terms apply.",
            timestamp = 1_756_000_200_000L
        )

        val result = service.parse(allParsers, raw)

        assertTrue("expected Ignored, got $result", result is IngestResult.Ignored)
    }

    @Test
    fun `unknown sender is unmatched`() {
        val raw = RawSms(
            id = 4L,
            sender = "01700000000",
            body = "Sent you Tk 500 for lunch, will settle up later.",
            timestamp = 1_756_000_300_000L
        )

        val result = service.parse(allParsers, raw)

        assertTrue("expected Unmatched, got $result", result is IngestResult.Unmatched)
    }

    @Test
    fun `sender matching a disabled parser is ignored rather than parsed`() {
        val raw = RawSms(
            id = 5L,
            sender = "16247",
            body = "Payment of Tk 100.00 to SAMPLE SHOP BD is successful. Balance Tk 900.00. " +
                "TrxID DEF34UVW88 at 01/09/2026 11:00",
            timestamp = 1_756_000_400_000L
        )

        val withoutBkash = allParsers.filterNot { it.getBankName() == "bKash" }
        val result = service.parse(withoutBkash, raw)

        assertTrue("expected Ignored, got $result", result is IngestResult.Ignored)
    }
}
