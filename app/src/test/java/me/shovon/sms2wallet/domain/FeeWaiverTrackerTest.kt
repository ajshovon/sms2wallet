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
    fun `decode rejects malformed entries`() {
        assertNull(FeeWaiverTracker.decode("City Bank|4412|24|3"))
        assertNull(FeeWaiverTracker.decode("|4412|24|3|15"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|0|3|15"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|24|4|31"))
        assertNull(FeeWaiverTracker.decode("City Bank|4412|many|3|15"))
    }
}
