package me.shovon.sms2wallet.data.local.dao

import androidx.room.ColumnInfo

/**
 * One push-log row joined to the transaction it refers to, so the Activity tab can show what
 * was pushed (merchant, amount) and not just that *something* was.
 *
 * The transaction columns are nullable because [me.shovon.sms2wallet.data.local.entity.PushLogEntity]
 * deliberately has no foreign key - the audit trail outlives the transaction it describes, so a
 * log row whose transaction has been deleted still appears, just without merchant/amount.
 */
data class PushLogWithTransaction(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: Long?,
    @ColumnInfo(name = "success") val success: Boolean,
    @ColumnInfo(name = "message") val message: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "merchant") val merchant: String?,
    @ColumnInfo(name = "amount") val amount: String?,
    @ColumnInfo(name = "type") val type: String?,
    @ColumnInfo(name = "push_state") val pushState: String?,
)
