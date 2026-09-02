package me.shovon.sms2wallet.data.local.dao

import androidx.room.ColumnInfo

/**
 * A distinct SMS source seen in the `transactions` table: the bank that sent it plus the
 * account last-4 the parser extracted (null when the SMS exposes none).
 *
 * Used by the Settings "Account mapping" section so a source the user has actually received
 * SMS from shows up as a mappable row even before any [me.shovon.sms2wallet.data.local.entity.AccountMappingEntity]
 * exists for it - otherwise the only mappable sources would be ones already mapped, and a new
 * bank could never be mapped in the first place.
 */
data class TransactionSource(
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_last4") val accountLast4: String?
)
