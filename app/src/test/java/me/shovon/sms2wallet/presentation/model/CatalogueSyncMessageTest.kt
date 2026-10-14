package me.shovon.sms2wallet.presentation.model

import me.shovon.sms2wallet.data.remote.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueSyncMessageTest {

    @Test
    fun `a sync says what arrived`() {
        assertEquals(
            "Synced 4 accounts and 38 categories from Wallet.",
            catalogueSyncMessage(ApiResult.Success(Unit, null), accountCount = 4, categoryCount = 38),
        )
        assertEquals(
            "Synced 1 account and 1 category from Wallet.",
            catalogueSyncMessage(ApiResult.Success(Unit, null), accountCount = 1, categoryCount = 1),
        )
    }

    @Test
    fun `a sync while Wallet is still applying changes says to try again`() {
        assertEquals(
            SERVER_STILL_SYNCING_NOTE,
            catalogueSyncMessage(ApiResult.Success(Unit, null, serverStillSyncing = true), 4, 38),
        )
    }

    @Test
    fun `failures say why nothing was synced`() {
        assertEquals(
            "Wallet rejected your token, so nothing was synced. Check it in Settings.",
            catalogueSyncMessage(ApiResult.Unauthorized, 0, 0),
        )
        assertEquals(
            "Wallet is still doing its first sync - try again in 3 minutes.",
            catalogueSyncMessage(ApiResult.SyncInProgress(retryAfterMinutes = 3), 0, 0),
        )
        assertEquals("Wallet returned an error (500).", catalogueSyncMessage(ApiResult.HttpError(500, null), 0, 0))
    }
}
