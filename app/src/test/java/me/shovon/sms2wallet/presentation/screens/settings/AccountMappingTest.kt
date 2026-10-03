package me.shovon.sms2wallet.presentation.screens.settings

import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.sms2wallet.data.local.entity.AccountMappingEntity
import me.shovon.sms2wallet.domain.model.WalletLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountMappingTest {

    @Test
    fun `formatDisplayBankName maps Mutual Trust Bank to MTB`() {
        assertEquals("MTB", SettingsViewModel.formatDisplayBankName("Mutual Trust Bank"))
        assertEquals("MTB", SettingsViewModel.formatDisplayBankName("Mutual Trust Bank Limited"))
        assertEquals("MTB", SettingsViewModel.formatDisplayBankName("Mutual Trust Bank Ltd"))
        assertEquals("MTB", SettingsViewModel.formatDisplayBankName("mutual trust bank"))
    }

    @Test
    fun `formatDisplayBankName leaves other bank names unchanged`() {
        assertEquals("bKash", SettingsViewModel.formatDisplayBankName("bKash"))
        assertEquals("BRAC Bank", SettingsViewModel.formatDisplayBankName("BRAC Bank"))
        assertEquals("City Bank", SettingsViewModel.formatDisplayBankName("City Bank"))
    }

    @Test
    fun `formatMappingSourceLabel replaces Mutual Trust Bank with MTB`() {
        assertEquals("MTB •••• 1234", formatMappingSourceLabel("Mutual Trust Bank •••• 1234"))
        assertEquals("MTB •••• 9876", formatMappingSourceLabel("mutual trust bank •••• 9876"))
        assertEquals("MTB", formatMappingSourceLabel("Mutual Trust Bank"))
        assertEquals("bKash •••• 0000", formatMappingSourceLabel("bKash •••• 0000"))
    }

    @Test
    fun `buildAccountMappingRows displays MTB with card number while preserving sourceId`() {
        val sources = listOf(
            TransactionSource(bankName = "Mutual Trust Bank", accountLast4 = "1234"),
            TransactionSource(bankName = "bKash", accountLast4 = "0000")
        )
        val mappings = listOf(
            AccountMappingEntity(
                bankName = "Mutual Trust Bank",
                accountLast4 = "1234",
                walletAccountId = "acc-mtb",
                walletAccountName = "MTB Account",
                autoPush = false,
                defaultCategoryId = null
            )
        )
        val accountLabels = listOf(
            WalletLabel(id = "acc-mtb", label = "MTB Account"),
            WalletLabel(id = "acc-bkash", label = "bKash Account")
        )

        val rows = SettingsViewModel.buildAccountMappingRows(sources, mappings, accountLabels)

        assertEquals(2, rows.size)

        val bkashRow = rows.first { it.sourceId == "bKash|0000" }
        assertEquals("bKash •••• 0000", bkashRow.sourceLabel)
        assertNull(bkashRow.mappedWalletAccountName)

        val mtbRow = rows.first { it.sourceId == "Mutual Trust Bank|1234" }
        // The display label uses MTB so account/card number is visible and not truncated
        assertEquals("MTB •••• 1234", mtbRow.sourceLabel)
        // Stored mapping resolution works
        assertEquals("MTB Account", mtbRow.mappedWalletAccountName)
        // sourceId preserves the database bank name for persistence
        assertEquals("Mutual Trust Bank|1234", mtbRow.sourceId)
    }

    @Test
    fun `buildAccountMappingRows without last4 displays MTB without trailing separator`() {
        val sources = listOf(
            TransactionSource(bankName = "Mutual Trust Bank", accountLast4 = null)
        )
        val mappings = emptyList<AccountMappingEntity>()
        val accountLabels = emptyList<WalletLabel>()

        val rows = SettingsViewModel.buildAccountMappingRows(sources, mappings, accountLabels)

        assertEquals(1, rows.size)
        val mtbRow = rows.first()
        assertEquals("MTB", mtbRow.sourceLabel)
        assertEquals("Mutual Trust Bank|", mtbRow.sourceId)
    }
}
