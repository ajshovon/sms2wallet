package me.shovon.sms2wallet.presentation.screens.settings

import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import me.shovon.sms2wallet.presentation.screens.settings.FeeWaiverSettingsViewModel.Companion.buildFeeWaiverSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `a new card is offered the other cards from its bank as supplementary`() {
        val state = buildFeeWaiverSettings(
            trackers = emptyList(),
            sources = listOf(
                TransactionSource("EBL", "1234"),
                TransactionSource("EBL", "5678"),
                TransactionSource("EBL", null),
                TransactionSource("City Bank", "4412"),
            ),
        )

        val ebl1234 = state.untrackedCards.single { it.sourceId == "EBL|1234" }
        // Not itself, not another bank, and not a source with no card number.
        assertEquals(listOf("5678"), ebl1234.supplementaryOptions.map { it.last4 })
        assertEquals("EBL •••• 5678", ebl1234.supplementaryOptions.single().label)
    }

    @Test
    fun `a linked supplementary card is neither offered for tracking nor offered again`() {
        val state = buildFeeWaiverSettings(
            trackers = listOf(
                FeeWaiverTracker("EBL", "1234", 24, 1, 1, supplementaryLast4s = listOf("5678")),
                FeeWaiverTracker("EBL", "9999", 12, 1, 1),
            ),
            sources = listOf(
                TransactionSource("EBL", "1234"),
                TransactionSource("EBL", "5678"),
                TransactionSource("EBL", "9999"),
                TransactionSource("EBL", "3333"),
            ),
        )

        assertEquals(listOf("EBL •••• 3333"), state.untrackedCards.map { it.label })
        val main = state.trackers.single { it.sourceId == "EBL|1234" }
        // Its own link stays editable; 9999 is tracked on its own, so linking it would double count.
        assertEquals(listOf("3333", "5678"), main.supplementaryOptions.map { it.last4 })
        val other = state.trackers.single { it.sourceId == "EBL|9999" }
        assertEquals(listOf("3333"), other.supplementaryOptions.map { it.last4 })
    }

    @Test
    fun `a same-bank card tracked on its own is named, not offered`() {
        val state = buildFeeWaiverSettings(
            trackers = listOf(
                FeeWaiverTracker("EBL", "1234", 24, 1, 1),
                FeeWaiverTracker("EBL", "5678", 24, 1, 1),
                FeeWaiverTracker("City Bank", "4412", 24, 1, 1),
            ),
            sources = listOf(TransactionSource("EBL", "1234"), TransactionSource("EBL", "5678")),
        )

        val main = state.trackers.single { it.sourceId == "EBL|1234" }
        assertEquals(emptyList<String>(), main.supplementaryOptions.map { it.last4 })
        // Only the same bank, and never the card itself.
        assertEquals(listOf("EBL •••• 5678"), main.trackedSeparatelyLabels)
    }

    @Test
    fun `a linked card stays unlinkable after its SMS are gone`() {
        val row = buildFeeWaiverSettings(
            trackers = listOf(FeeWaiverTracker("EBL", "1234", 24, 1, 1, supplementaryLast4s = listOf("5678"))),
            sources = listOf(TransactionSource("EBL", "1234")),
        ).trackers.single()

        assertEquals(listOf("5678"), row.supplementaryLast4s)
        assertEquals(listOf("5678"), row.supplementaryOptions.map { it.last4 })
        assertTrue(row.summary.endsWith(" · +1 supplementary"))
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
