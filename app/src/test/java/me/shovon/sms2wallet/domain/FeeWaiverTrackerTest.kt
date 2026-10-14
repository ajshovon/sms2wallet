package me.shovon.sms2wallet.domain

import java.time.LocalDate
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeeWaiverTrackerTest {

    private fun tracker(month: Int, day: Int) = FeeWaiverTracker("City Bank", "4412", 24, month, day)

    @Test
    fun `a date after this year's start is in the fee year that began this year`() {
        val period = tracker(3, 15).periodContaining(LocalDate.of(2026, 10, 14))

        assertEquals(LocalDate.of(2026, 3, 15), period.start)
        assertEquals(LocalDate.of(2027, 3, 14), period.endInclusive)
    }

    @Test
    fun `a date before this year's start is in the fee year that began last year`() {
        val period = tracker(11, 1).periodContaining(LocalDate.of(2026, 10, 14))

        assertEquals(LocalDate.of(2025, 11, 1), period.start)
        assertEquals(LocalDate.of(2026, 10, 31), period.endInclusive)
    }

    @Test
    fun `the start date itself opens a new fee year`() {
        val period = tracker(10, 14).periodContaining(LocalDate.of(2026, 10, 14))

        assertEquals(LocalDate.of(2026, 10, 14), period.start)
    }

    @Test
    fun `a 1 January start is the calendar year`() {
        val period = tracker(1, 1).periodContaining(LocalDate.of(2026, 10, 14))

        assertEquals(LocalDate.of(2026, 1, 1), period.start)
        assertEquals(LocalDate.of(2026, 12, 31), period.endInclusive)
    }

    @Test
    fun `a 29 February start falls back to the 28th in a non-leap year`() {
        val period = tracker(2, 29).periodContaining(LocalDate.of(2026, 10, 14))

        assertEquals(LocalDate.of(2026, 2, 28), period.start)
        assertEquals(LocalDate.of(2027, 2, 27), period.endInclusive)
    }

    @Test
    fun `a 29 February start is kept in a leap year`() {
        val period = tracker(2, 29).periodContaining(LocalDate.of(2028, 3, 1))

        assertEquals(LocalDate.of(2028, 2, 29), period.start)
        assertEquals(LocalDate.of(2029, 2, 27), period.endInclusive)
    }

    @Test
    fun `encode and decode round trip, including a card with no last-4`() {
        val withDigits = tracker(3, 15)
        val withoutDigits = FeeWaiverTracker("bKash", "", 12, 1, 1)

        assertEquals(withDigits, FeeWaiverTracker.decode(FeeWaiverTracker.encode(withDigits)))
        assertEquals(withoutDigits, FeeWaiverTracker.decode(FeeWaiverTracker.encode(withoutDigits)))
    }

    @Test
    fun `supplementary cards round trip`() {
        val tracker = FeeWaiverTracker("EBL", "1234", 24, 1, 1, supplementaryLast4s = listOf("5678", "9012"))

        assertEquals(tracker, FeeWaiverTracker.decode(FeeWaiverTracker.encode(tracker)))
        assertEquals(listOf("1234", "5678", "9012"), tracker.allLast4s)
    }

    @Test
    fun `a tracker without supplementary cards encodes as 0_9_0 did`() {
        // 0.9.0 rejects anything but five fields, so this is what keeps a downgrade readable.
        assertEquals("EBL|1234|24|1|1", FeeWaiverTracker.encode(FeeWaiverTracker("EBL", "1234", 24, 1, 1)))
    }

    @Test
    fun `an entry written by 0_9_0 decodes with no supplementary cards`() {
        val tracker = FeeWaiverTracker.decode("EBL|1234|24|1|15")

        assertEquals(FeeWaiverTracker("EBL", "1234", 24, 1, 15), tracker)
        assertEquals(emptyList<String>(), tracker?.supplementaryLast4s)
    }

    @Test
    fun `decode drops blank, repeated and self-referencing supplementary entries`() {
        val tracker = FeeWaiverTracker.decode("EBL|1234|24|1|1|5678,,1234,5678")

        assertEquals(listOf("5678"), tracker?.supplementaryLast4s)
    }

    @Test
    fun `decode rejects malformed entries`() {
        assertNull(FeeWaiverTracker.decode("City Bank|4412|24|3"))
        assertNull(FeeWaiverTracker.decode("|4412|24|3|15"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|0|3|15"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|24|4|31"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|many|3|15"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|24|3|15|5678|extra"))
    }
}
