package me.shovon.sms2wallet.presentation.screens.settings

import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import me.shovon.sms2wallet.presentation.screens.settings.FeeWaiverSettingsViewModel.Companion.buildFeeWaiverSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeeWaiverSettingsTest {

    @Test
    fun `tracked cards are listed and not offered again`() {
        val state = buildFeeWaiverSettings(
            trackers = listOf(FeeWaiverTracker("City Bank", "4412", 24, 3, 15)),
            sources = listOf(
                TransactionSource("City Bank", "4412"),
                TransactionSource("EBL", "0917"),
            ),
        )

        assertFalse(state.isLoading)
        assertEquals(listOf("City Bank •••• 4412"), state.trackers.map { it.cardLabel })
        assertEquals(listOf("EBL •••• 0917"), state.untrackedCards.map { it.label })
        assertEquals("EBL|0917", state.untrackedCards.single().sourceId)
    }

    @Test
    fun `a null and an empty last-4 are one card`() {
        val state = buildFeeWaiverSettings(
            trackers = emptyList(),
            sources = listOf(TransactionSource("Nagad", null), TransactionSource("Nagad", "")),
        )

        assertEquals(listOf("Nagad"), state.untrackedCards.map { it.label })
        assertEquals("Nagad|", state.untrackedCards.single().sourceId)
    }

    @Test
    fun `a tracker on a card with no last-4 hides that card from the add list`() {
        val state = buildFeeWaiverSettings(
            trackers = listOf(FeeWaiverTracker("Nagad", "", 10, 1, 1)),
            sources = listOf(TransactionSource("Nagad", null)),
        )

        assertEquals(emptyList<String>(), state.untrackedCards.map { it.label })
    }

    @Test
    fun `untracked cards are sorted by label`() {
        val state = buildFeeWaiverSettings(
            trackers = emptyList(),
            sources = listOf(TransactionSource("EBL", "0917"), TransactionSource("City Bank", "4412")),
        )

        assertEquals(listOf("City Bank •••• 4412", "EBL •••• 0917"), state.untrackedCards.map { it.label })
    }

    @Test
    fun `sorting ignores case so lowercase brands are not pushed to the end`() {
        val state = buildFeeWaiverSettings(
            trackers = emptyList(),
            sources = listOf(TransactionSource("Mutual Trust Bank", "0000"), TransactionSource("bKash", null)),
        )

        assertEquals(listOf("bKash", "MTB •••• 0000"), state.untrackedCards.map { it.label })
    }

    @Test
    fun `a tracker row carries what the editor needs`() {
        val row = buildFeeWaiverSettings(
            trackers = listOf(FeeWaiverTracker("City Bank", "4412", 18, 2, 29)),
            sources = emptyList(),
        ).trackers.single()

        assertEquals("City Bank|4412", row.sourceId)
        assertEquals(18, row.target)
        assertEquals(2, row.startMonth)
        assertEquals(29, row.startDay)
    }
}
