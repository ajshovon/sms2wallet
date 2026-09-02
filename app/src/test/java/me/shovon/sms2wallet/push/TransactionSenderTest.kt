package me.shovon.sms2wallet.push

import kotlinx.coroutines.test.runTest
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.data.push.TransactionSender
import me.shovon.sms2wallet.data.remote.ApiResult
import me.shovon.sms2wallet.data.remote.RateLimitInfo
import me.shovon.sms2wallet.data.remote.dto.CreateRecordRequest
import me.shovon.sms2wallet.data.remote.dto.CreateRecordsResponse
import me.shovon.sms2wallet.data.remote.dto.RecordResultDto
import me.shovon.sms2wallet.data.remote.dto.RecordsSummaryDto
import me.shovon.sms2wallet.domain.model.PushState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for the send pipeline.
 *
 * These matter more than usual: the pipeline is the part that can create a duplicate record in
 * someone's real budget, and the Wallet API has no idempotency key to fall back on. Every test
 * below is really asking "can this outcome cause the same transaction to be POSTed twice?".
 *
 * Synthetic fixtures only - ids, amounts and merchant names are invented.
 */
class TransactionSenderTest {

    private fun queuedRow(
        id: Long,
        amount: String = "1899.00",
        type: String = TransactionType.EXPENSE.name,
        accountId: String? = "acc-1",
        categoryId: String? = "cat-1",
        attemptCount: Int = 1,
    ) = TransactionEntity(
        id = id,
        transactionHash = "hash-$id",
        bankName = "bKash",
        accountLast4 = "1234",
        amount = amount,
        type = type,
        merchant = "SYNTHETIC MERCHANT",
        reference = "TRX$id",
        currency = "BDT",
        smsSender = "bKash",
        smsBody = "synthetic",
        timestamp = 1_760_000_000_000L,
        pushState = PushState.SENDING.name,
        walletRecordId = null,
        walletAccountId = accountId,
        walletCategoryId = categoryId,
        lastError = null,
        attemptCount = attemptCount,
        suspectedDuplicateOfId = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun sender(
        claimed: List<TransactionEntity>,
        api: FakeWalletApi,
        hasToken: Boolean = true,
    ): Pair<TransactionSender, FakeTransactionDao> {
        val dao = FakeTransactionDao(claimed)
        val sender = TransactionSender(
            transactionDao = dao,
            pushLogDao = FakePushLogDao(),
            walletApiClient = api,
            hasToken = { hasToken },
        )
        return sender to dao
    }

    // ---- The happy path the app never had ---------------------------------

    @Test
    fun `a queued transaction is POSTed and marked pushed with the returned record id`() = runTest {
        val api = FakeWalletApi(
            ApiResult.Success(
                CreateRecordsResponse(
                    summary = RecordsSummaryDto(total = 1, succeeded = 1),
                    results = listOf(RecordResultDto(inputIndex = 0, success = true, id = "wallet-rec-9")),
                ),
                RateLimitInfo(300, 299),
            )
        )
        val (sender, dao) = sender(listOf(queuedRow(1)), api)

        assertEquals(TransactionSender.Outcome.Done, sender.sendQueued())

        assertEquals(PushState.PUSHED.name, dao.rows.getValue(1).pushState)
        assertEquals("wallet-rec-9", dao.rows.getValue(1).walletRecordId)
        assertEquals(1, api.sent.size)
    }

    @Test
    fun `expense is sent as a negative amount and income as positive`() = runTest {
        val api = FakeWalletApi(ApiResult.Success(CreateRecordsResponse(), null))
        val rows = listOf(
            queuedRow(1, amount = "1899.00", type = TransactionType.EXPENSE.name),
            queuedRow(2, amount = "2450.00", type = TransactionType.INCOME.name),
        )
        val (sender, _) = sender(rows, api)

        sender.sendQueued()

        // The API has no income/expense field - the sign of the amount is the only thing
        // carrying it, so getting this backwards silently inverts someone's budget.
        assertEquals(-1899.00, api.sent.single()[0].amount.value, 0.001)
        assertEquals(2450.00, api.sent.single()[1].amount.value, 0.001)
    }

    // ---- Never send twice --------------------------------------------------

    @Test
    fun `an ambiguous network failure goes to NEEDS_VERIFY, never back to QUEUED`() = runTest {
        val api = FakeWalletApi(ApiResult.NetworkError("timeout after write", ambiguous = true))
        val (sender, dao) = sender(listOf(queuedRow(1)), api)

        sender.sendQueued()

        // QUEUED would make it eligible for another POST of a record the server may already hold.
        assertEquals(PushState.NEEDS_VERIFY.name, dao.rows.getValue(1).pushState)
    }

    @Test
    fun `an unambiguous offline failure is safely requeued for retry`() = runTest {
        val api = FakeWalletApi(ApiResult.NetworkError("connection refused", ambiguous = false))
        val (sender, dao) = sender(listOf(queuedRow(1)), api)

        val outcome = sender.sendQueued()

        assertTrue(outcome is TransactionSender.Outcome.Retry)
        // Nothing left the device, so re-queueing cannot duplicate anything.
        assertEquals(PushState.QUEUED.name, dao.rows.getValue(1).pushState)
    }

    @Test
    fun `a row the batch response never mentions is verified, not retried`() = runTest {
        val api = FakeWalletApi(
            ApiResult.Success(
                CreateRecordsResponse(
                    summary = RecordsSummaryDto(total = 2, succeeded = 1),
                    // Only index 0 comes back; index 1's fate is unknown.
                    results = listOf(RecordResultDto(inputIndex = 0, success = true, id = "rec-a")),
                ),
                null,
            )
        )
        val (sender, dao) = sender(listOf(queuedRow(1), queuedRow(2)), api)

        sender.sendQueued()

        assertEquals(PushState.PUSHED.name, dao.rows.getValue(1).pushState)
        assertEquals(PushState.NEEDS_VERIFY.name, dao.rows.getValue(2).pushState)
    }

    @Test
    fun `success without a record id is verified rather than assumed pushed`() = runTest {
        val api = FakeWalletApi(
            ApiResult.Success(
                CreateRecordsResponse(results = listOf(RecordResultDto(inputIndex = 0, success = true, id = null))),
                null,
            )
        )
        val (sender, dao) = sender(listOf(queuedRow(1)), api)

        sender.sendQueued()

        assertEquals(PushState.NEEDS_VERIFY.name, dao.rows.getValue(1).pushState)
        assertNull(dao.rows.getValue(1).walletRecordId)
    }

    // ---- Per-item results in a non-atomic batch ----------------------------

    @Test
    fun `a partial batch applies each item's own outcome by inputIndex`() = runTest {
        val api = FakeWalletApi(
            ApiResult.Success(
                CreateRecordsResponse(
                    results = listOf(
                        // Deliberately out of order: inputIndex, not position, is the join key.
                        RecordResultDto(inputIndex = 1, success = false, error = "bad category", errorType = "client_error"),
                        RecordResultDto(inputIndex = 0, success = true, id = "rec-ok"),
                        RecordResultDto(inputIndex = 2, success = false, error = "boom", errorType = "server_error"),
                    ),
                ),
                null,
            )
        )
        val (sender, dao) = sender(listOf(queuedRow(1), queuedRow(2), queuedRow(3)), api)

        sender.sendQueued()

        assertEquals(PushState.PUSHED.name, dao.rows.getValue(1).pushState)
        assertEquals(PushState.FAILED_PERMANENT.name, dao.rows.getValue(2).pushState)
        assertEquals(PushState.FAILED_RETRYABLE.name, dao.rows.getValue(3).pushState)
    }

    // ---- Failing fast without spending the rate limit ----------------------

    @Test
    fun `a row with no wallet account is failed locally with no HTTP call`() = runTest {
        val api = FakeWalletApi(ApiResult.Success(CreateRecordsResponse(), null))
        val (sender, dao) = sender(listOf(queuedRow(1, accountId = null)), api)

        sender.sendQueued()

        assertEquals(0, api.sent.size)
        assertEquals(PushState.FAILED_PERMANENT.name, dao.rows.getValue(1).pushState)
    }

    @Test
    fun `a row with no category is still sent`() = runTest {
        val api = FakeWalletApi(ApiResult.Success(CreateRecordsResponse(), null))
        val (sender, _) = sender(listOf(queuedRow(1, categoryId = null)), api)

        sender.sendQueued()

        // categoryId is optional per the OpenAPI schema; withholding these rows would strand
        // every uncategorised transaction for a rejection the server would never have made.
        assertEquals(1, api.sent.size)
        assertNull(api.sent.single().single().categoryId)
    }

    @Test
    fun `nothing is claimed when no token is configured`() = runTest {
        val api = FakeWalletApi(ApiResult.Success(CreateRecordsResponse(), null))
        val (sender, dao) = sender(listOf(queuedRow(1)), api, hasToken = false)

        assertEquals(TransactionSender.Outcome.NoToken, sender.sendQueued())

        assertEquals(0, api.sent.size)
        // Untouched: a row must not be stranded in SENDING for a request never attempted.
        assertEquals(PushState.SENDING.name, dao.rows.getValue(1).pushState)
        assertEquals(0, dao.claimCallCount)
    }

    @Test
    fun `an invalid token fails the batch instead of retrying on a timer`() = runTest {
        val api = FakeWalletApi(ApiResult.Unauthorized)
        val (sender, dao) = sender(listOf(queuedRow(1)), api)

        assertEquals(TransactionSender.Outcome.Done, sender.sendQueued())

        assertEquals(PushState.FAILED_PERMANENT.name, dao.rows.getValue(1).pushState)
        assertTrue(dao.rows.getValue(1).lastError!!.contains("token", ignoreCase = true))
    }

    @Test
    fun `rate limiting requeues and asks the caller to retry`() = runTest {
        val api = FakeWalletApi(ApiResult.RateLimited(retryAfterSeconds = 60))
        val (sender, dao) = sender(listOf(queuedRow(1)), api)

        assertTrue(sender.sendQueued() is TransactionSender.Outcome.Retry)
        assertEquals(PushState.QUEUED.name, dao.rows.getValue(1).pushState)
    }

    @Test
    fun `a row stops auto-retrying once it has burned through its attempts`() = runTest {
        val api = FakeWalletApi(ApiResult.NetworkError("offline", ambiguous = false))
        val (sender, dao) = sender(listOf(queuedRow(1, attemptCount = 5)), api)

        sender.sendQueued()

        // Otherwise an unreachable server would spin this row forever.
        assertEquals(PushState.FAILED_RETRYABLE.name, dao.rows.getValue(1).pushState)
    }
}

/** Records what was sent and replays one canned outcome. */
private class FakeWalletApi(
    private val createResult: ApiResult<CreateRecordsResponse>,
    private val findResult: ApiResult<List<me.shovon.sms2wallet.data.remote.dto.RecordDto>> =
        ApiResult.Success(emptyList(), null),
) : me.shovon.sms2wallet.data.remote.WalletApiClient {
    val sent = mutableListOf<List<CreateRecordRequest>>()

    override suspend fun createRecords(requests: List<CreateRecordRequest>): ApiResult<CreateRecordsResponse> {
        sent += requests
        return createResult
    }

    override suspend fun listAccounts() = ApiResult.Success(emptyList<me.shovon.sms2wallet.data.remote.dto.WalletAccountDto>(), null)
    override suspend fun listCategories() = ApiResult.Success(emptyList<me.shovon.sms2wallet.data.remote.dto.WalletCategoryDto>(), null)
    override suspend fun findRecords(accountId: String, dayIso: String, amount: String, source: String?) = findResult
    override suspend fun validateToken() = ApiResult.Success(Unit, null)
    override suspend fun usageStats() = ApiResult.Success(me.shovon.sms2wallet.data.remote.dto.UsageStatsDto(), null)
}

/**
 * Reconciliation is the other half of the never-send-twice guarantee: it is what gets an
 * ambiguous row unstuck without guessing.
 */
class TransactionReconcilerTest {

    private fun sendingRow(id: Long, state: PushState) = TransactionEntity(
        id = id,
        transactionHash = "hash-$id",
        bankName = "bKash",
        accountLast4 = "1234",
        amount = "500.00",
        type = TransactionType.EXPENSE.name,
        merchant = "SYNTHETIC",
        reference = null,
        currency = "BDT",
        smsSender = "bKash",
        smsBody = "synthetic",
        timestamp = 1_760_000_000_000L,
        pushState = state.name,
        walletRecordId = null,
        walletAccountId = "acc-1",
        walletCategoryId = "cat-1",
        lastError = null,
        attemptCount = 1,
        suspectedDuplicateOfId = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `a record the server already holds is adopted, not sent again`() = runTest {
        val dao = FakeTransactionDao(listOf(sendingRow(1, PushState.NEEDS_VERIFY)))
        val api = FakeWalletApi(
            ApiResult.Success(CreateRecordsResponse(), null),
            findResult = ApiResult.Success(
                listOf(
                    me.shovon.sms2wallet.data.remote.dto.RecordDto(
                        id = "existing-rec-1",
                        accountId = "acc-1",
                        amount = me.shovon.sms2wallet.data.remote.dto.RecordAmount(-500.0),
                        recordDate = "2025-10-09",
                    )
                ),
                null,
            ),
        )
        val reconciler = me.shovon.sms2wallet.data.push.TransactionReconciler(dao, FakePushLogDao(), api)

        reconciler.reconcile()

        assertEquals(PushState.PUSHED.name, dao.rows.getValue(1).pushState)
        assertEquals("existing-rec-1", dao.rows.getValue(1).walletRecordId)
    }

    @Test
    fun `a record the server does not have is safely queued again`() = runTest {
        val dao = FakeTransactionDao(listOf(sendingRow(1, PushState.NEEDS_VERIFY)))
        val api = FakeWalletApi(
            ApiResult.Success(CreateRecordsResponse(), null),
            findResult = ApiResult.Success(emptyList(), null),
        )
        val reconciler = me.shovon.sms2wallet.data.push.TransactionReconciler(dao, FakePushLogDao(), api)

        assertTrue(reconciler.reconcile())
        assertEquals(PushState.QUEUED.name, dao.rows.getValue(1).pushState)
    }

    @Test
    fun `an unreachable server leaves the row untouched rather than guessing`() = runTest {
        val dao = FakeTransactionDao(listOf(sendingRow(1, PushState.NEEDS_VERIFY)))
        val api = FakeWalletApi(
            ApiResult.Success(CreateRecordsResponse(), null),
            findResult = ApiResult.NetworkError("offline", ambiguous = false),
        )
        val reconciler = me.shovon.sms2wallet.data.push.TransactionReconciler(dao, FakePushLogDao(), api)

        assertFalse(reconciler.reconcile())
        // Still unknown: moving it either way here is exactly how a duplicate gets created.
        assertEquals(PushState.NEEDS_VERIFY.name, dao.rows.getValue(1).pushState)
    }
}
