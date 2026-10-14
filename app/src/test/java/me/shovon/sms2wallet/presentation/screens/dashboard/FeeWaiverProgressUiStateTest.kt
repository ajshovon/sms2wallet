package me.shovon.sms2wallet.presentation.screens.dashboard

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import me.shovon.sms2wallet.domain.model.FeeWaiverProgress
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import me.shovon.sms2wallet.presentation.model.toUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeWaiverProgressUiStateTest {

    private val tracker = FeeWaiverTracker("City Bank", "4412", 24, 3, 15)
    private val period = LocalDate.of(2026, 3, 15)..LocalDate.of(2027, 3, 14)

    // Built with the production pattern so the assertions hold in any device language.
    private fun date(value: LocalDate) = value.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    private fun progress(count: Int, asOf: LocalDate) = FeeWaiverProgress(tracker, period, count, asOf).toUiState()

    @Test
    fun `behind target says how many more and by when`() {
        val state = progress(count = 14, asOf = LocalDate.of(2026, 10, 14))

        assertEquals("City Bank •••• 4412", state.cardLabel)
        assertEquals(14, state.count)
        assertEquals(24, state.target)
        assertFalse(state.isMet)
        assertEquals("10 more by ${date(period.endInclusive)} · 152 days left", state.statusLabel)
    }

    @Test
    fun `the last day of the fee year counts as one day left`() {
        val state = progress(count = 23, asOf = period.endInclusive)

        assertEquals("1 more by ${date(period.endInclusive)} · 1 day left", state.statusLabel)
    }

    @Test
    fun `reaching the target says when counting resets`() {
        val state = progress(count = 24, asOf = LocalDate.of(2026, 10, 14))

        assertTrue(state.isMet)
        assertEquals("Target reached · resets ${date(LocalDate.of(2027, 3, 15))}", state.statusLabel)
    }

    @Test
    fun `going past the target fills the bar without overflowing it`() {
        val state = progress(count = 30, asOf = LocalDate.of(2026, 10, 14))

        assertTrue(state.isMet)
        assertEquals(1f, state.fraction, 0.0001f)
    }

    @Test
    fun `supplementary cards are named on the row`() {
        val withSupplementary = FeeWaiverProgress(
            tracker.copy(supplementaryLast4s = listOf("5678")), period, 40, LocalDate.of(2026, 10, 14)
        ).toUiState()

        assertEquals("Includes supplementary •••• 5678", withSupplementary.supplementaryLabel)
        assertEquals(null, progress(count = 14, asOf = LocalDate.of(2026, 10, 14)).supplementaryLabel)
    }

    @Test
    fun `fraction is the share of the target reached`() {
        val state = progress(count = 6, asOf = LocalDate.of(2026, 10, 14))

        assertEquals(0.25f, state.fraction, 0.0001f)
    }
}
