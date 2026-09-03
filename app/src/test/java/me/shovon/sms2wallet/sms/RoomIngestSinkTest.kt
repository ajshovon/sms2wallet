package me.shovon.sms2wallet.sms

import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.shovon.bdparser.ParsedTransaction
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.data.local.dao.AccountMappingDao
import me.shovon.sms2wallet.data.local.dao.CategoryRuleDao
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.local.dao.TransactionSource
import me.shovon.sms2wallet.data.local.dao.UnmatchedSmsDao
import me.shovon.sms2wallet.data.local.entity.AccountMappingEntity
import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.data.local.entity.UnmatchedSmsEntity
import me.shovon.sms2wallet.data.sms.IngestResult
import me.shovon.sms2wallet.data.sms.RawSms
import me.shovon.sms2wallet.data.sms.RoomIngestSink
import me.shovon.sms2wallet.domain.model.PushState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [RoomIngestSink] against in-memory fakes of the four DAOs it depends on. Pure JVM
 * JUnit 4 only - no Robolectric, no Android framework. All senders/merchants/amounts below are
 * synthetic fixtures, not tied to any real person or account.
 */
class RoomIngestSinkTest {

    private val sampleBank = "SAMPLE BANK"
    private val sampleLast4 = "1234"
    private val sampleWalletAccountId = "wallet-acc-synthetic-001"

    private fun sink(
        transactionDao: FakeTransactionDao = FakeTransactionDao(),
        accountMappingDao: FakeAccountMappingDao = FakeAccountMappingDao(),
        categoryRuleDao: FakeCategoryRuleDao = FakeCategoryRuleDao(),
        unmatchedSmsDao: FakeUnmatchedSmsDao = FakeUnmatchedSmsDao(),
        autoPushBankNames: suspend () -> Set<String> = { emptySet() },
    ) = RoomIngestSink(
        transactionDao = transactionDao,
        accountMappingDao = accountMappingDao,
        categoryRuleDao = categoryRuleDao,
        unmatchedSmsDao = unmatchedSmsDao,
        autoPushBankNames = autoPushBankNames,
        // No synced categories in these tests, so the built-in merchant guesser stays inert and
        // the expectations below are about the rule/mapping logic only.
        walletCategories = { emptyList() },
        // The default debugLog hits android.util.Log, which is unmocked under plain JUnit (no
        // Robolectric here) - use a no-op so exercising the dedup path doesn't crash the test.
        debugLog = {},
    )

    private fun sampleTransaction(
        amount: BigDecimal = BigDecimal("500.00"),
        timestamp: Long = 1_756_000_000_000L,
        smsBody: String = "Sample synthetic debit of Tk 500.00 at SAMPLE SHOP BD. Ref TESTREF01.",
        merchant: String? = "SAMPLE SHOP BD",
    ) = ParsedTransaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        merchant = merchant,
        reference = "TESTREF01",
        accountLast4 = sampleLast4,
        balance = BigDecimal("1000.00"),
        smsBody = smsBody,
        sender = "01700000000",
        timestamp = timestamp,
        bankName = sampleBank,
    )

    private fun sampleRaw(sender: String = "01700000000", body: String = "synthetic body", timestamp: Long = 1L) =
        RawSms(id = -1L, sender = sender, body = body, timestamp = timestamp)

    // ---- 1. L1 dedup ----------------------------------------------------------

    @Test
    fun `same SMS ingested twice is deduplicated with no side effect`() = runTest {
        val transactionDao = FakeTransactionDao()
        val ingestSink = sink(transactionDao = transactionDao)
        val transaction = sampleTransaction()

        ingestSink.accept(IngestResult.Parsed(transaction), sampleRaw())
        assertEquals(1, transactionDao.rows.size)

        ingestSink.accept(IngestResult.Parsed(transaction), sampleRaw())

        assertEquals(1, transactionDao.rows.size)
        assertEquals(-1L, transactionDao.lastInsertResult)
    }

    // ---- 2. Unmapped is never QUEUED, even with auto-push enabled -------------

    @Test
    fun `unmapped source with auto-push enabled is PARSED not QUEUED`() = runTest {
        val transactionDao = FakeTransactionDao()
        val ingestSink = sink(
            transactionDao = transactionDao,
            // Auto-push is enabled for this bank, but no AccountMappingEntity exists for it -
            // there is no Wallet account to route to, so this must still land in PARSED.
            autoPushBankNames = { setOf(sampleBank) },
        )

        ingestSink.accept(IngestResult.Parsed(sampleTransaction()), sampleRaw())

        val row = transactionDao.rows.values.single()
        assertEquals(PushState.PARSED.name, row.pushState)
    }

    // ---- 3. Mapped + auto-push enabled -> QUEUED -------------------------------

    @Test
    fun `mapped source with auto-push enabled is QUEUED`() = runTest {
        val transactionDao = FakeTransactionDao()
        val accountMappingDao = FakeAccountMappingDao().apply {
            mappings += AccountMappingEntity(
                id = 1L,
                bankName = sampleBank,
                accountLast4 = sampleLast4,
                walletAccountId = sampleWalletAccountId,
                walletAccountName = "Synthetic Wallet Account",
                autoPush = true,
                defaultCategoryId = null,
            )
        }
        val ingestSink = sink(
            transactionDao = transactionDao,
            accountMappingDao = accountMappingDao,
            autoPushBankNames = { setOf(sampleBank) },
        )

        ingestSink.accept(IngestResult.Parsed(sampleTransaction()), sampleRaw())

        val row = transactionDao.rows.values.single()
        assertEquals(PushState.QUEUED.name, row.pushState)
        assertEquals(sampleWalletAccountId, row.walletAccountId)
    }

    // ---- 4. Mapped + auto-push disabled -> PARSED ------------------------------

    @Test
    fun `mapped source with auto-push disabled is PARSED`() = runTest {
        val transactionDao = FakeTransactionDao()
        val accountMappingDao = FakeAccountMappingDao().apply {
            mappings += AccountMappingEntity(
                id = 1L,
                bankName = sampleBank,
                accountLast4 = sampleLast4,
                walletAccountId = sampleWalletAccountId,
                walletAccountName = "Synthetic Wallet Account",
                autoPush = false,
                defaultCategoryId = null,
            )
        }
        val ingestSink = sink(
            transactionDao = transactionDao,
            accountMappingDao = accountMappingDao,
            autoPushBankNames = { emptySet() },
        )

        ingestSink.accept(IngestResult.Parsed(sampleTransaction()), sampleRaw())

        val row = transactionDao.rows.values.single()
        assertEquals(PushState.PARSED.name, row.pushState)
    }

    // ---- 5. Suspected cross-provider duplicate forces PARSED -------------------

    @Test
    fun `suspected duplicate is PARSED with suspectedDuplicateOfId set even when auto-push is on`() = runTest {
        val transactionDao = FakeTransactionDao()
        val accountMappingDao = FakeAccountMappingDao().apply {
            mappings += AccountMappingEntity(
                id = 1L,
                bankName = sampleBank,
                accountLast4 = sampleLast4,
                walletAccountId = sampleWalletAccountId,
                walletAccountName = "Synthetic Wallet Account",
                autoPush = true,
                defaultCategoryId = null,
            )
        }
        val existingTimestamp = 1_756_000_000_000L
        val existingId = transactionDao.insertIgnore(
            sampleEntity(
                transactionHash = "existing-hash-from-another-provider",
                walletAccountId = sampleWalletAccountId,
                amount = "500.00",
                timestamp = existingTimestamp,
                pushState = PushState.QUEUED,
            )
        )
        assertTrue("fixture row must have inserted", existingId > 0)

        val ingestSink = sink(
            transactionDao = transactionDao,
            accountMappingDao = accountMappingDao,
            autoPushBankNames = { setOf(sampleBank) },
        )

        // Same amount, within the +/-10 minute window, but a distinct SMS body/hash - simulates
        // a second source (e.g. a merchant confirmation SMS) reporting the same real payment.
        val duplicateReportingTransaction = sampleTransaction(
            amount = BigDecimal("500.00"),
            timestamp = existingTimestamp + 60_000L,
            smsBody = "A different SMS body reporting the same underlying payment.",
        )

        ingestSink.accept(IngestResult.Parsed(duplicateReportingTransaction), sampleRaw())

        val newRow = transactionDao.rows.values.single { it.id != existingId }
        assertEquals(PushState.PARSED.name, newRow.pushState)
        assertEquals(existingId, newRow.suspectedDuplicateOfId)
    }

    @Test
    fun `no false positive when no existing transaction matches window or account`() = runTest {
        val transactionDao = FakeTransactionDao()
        val accountMappingDao = FakeAccountMappingDao().apply {
            mappings += AccountMappingEntity(
                id = 1L,
                bankName = sampleBank,
                accountLast4 = sampleLast4,
                walletAccountId = sampleWalletAccountId,
                walletAccountName = "Synthetic Wallet Account",
                autoPush = true,
                defaultCategoryId = null,
            )
        }
        val ingestSink = sink(
            transactionDao = transactionDao,
            accountMappingDao = accountMappingDao,
            autoPushBankNames = { setOf(sampleBank) },
        )

        ingestSink.accept(IngestResult.Parsed(sampleTransaction()), sampleRaw())

        val row = transactionDao.rows.values.single()
        assertEquals(PushState.QUEUED.name, row.pushState)
        assertNull(row.suspectedDuplicateOfId)
    }

    // ---- 6. Unmatched lands in unmatched_sms ------------------------------------

    @Test
    fun `unmatched result is stored in unmatched_sms`() = runTest {
        val unmatchedSmsDao = FakeUnmatchedSmsDao()
        val ingestSink = sink(unmatchedSmsDao = unmatchedSmsDao)
        val raw = sampleRaw(sender = "01700000001", body = "Synthetic unrecognised SMS body.", timestamp = 42L)

        ingestSink.accept(IngestResult.Unmatched("No enabled parser recognises sender"), raw)

        val stored = unmatchedSmsDao.rows.single()
        assertEquals(raw.sender, stored.sender)
        assertEquals(raw.body, stored.body)
        assertEquals(raw.timestamp, stored.timestamp)
        assertEquals("No enabled parser recognises sender", stored.reason)
    }

    @Test
    fun `ignored result stores nothing`() = runTest {
        val transactionDao = FakeTransactionDao()
        val unmatchedSmsDao = FakeUnmatchedSmsDao()
        val ingestSink = sink(transactionDao = transactionDao, unmatchedSmsDao = unmatchedSmsDao)

        ingestSink.accept(IngestResult.Ignored("Promotional sender"), sampleRaw())

        assertTrue(transactionDao.rows.isEmpty())
        assertTrue(unmatchedSmsDao.rows.isEmpty())
    }

    private fun sampleEntity(
        transactionHash: String,
        walletAccountId: String?,
        amount: String,
        timestamp: Long,
        pushState: PushState,
    ) = TransactionEntity(
        transactionHash = transactionHash,
        bankName = sampleBank,
        accountLast4 = sampleLast4,
        amount = amount,
        type = TransactionType.EXPENSE.name,
        merchant = "SAMPLE SHOP BD",
        reference = "TESTREF00",
        currency = "BDT",
        smsSender = "01700000000",
        smsBody = "Synthetic fixture SMS body.",
        timestamp = timestamp,
        pushState = pushState.name,
        walletRecordId = null,
        walletAccountId = walletAccountId,
        walletCategoryId = null,
        lastError = null,
        attemptCount = 0,
        suspectedDuplicateOfId = null,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

/** In-memory fake of [TransactionDao] backed by a mutable map keyed by autogenerated row id. */
private class FakeTransactionDao : TransactionDao {
    val rows = mutableMapOf<Long, TransactionEntity>()
    var lastInsertResult: Long = 0L
        private set
    private var nextId = 1L

    override suspend fun insertIgnore(transaction: TransactionEntity): Long {
        val alreadyExists = rows.values.any { it.transactionHash == transaction.transactionHash }
        if (alreadyExists) {
            lastInsertResult = -1L
            return -1L
        }
        val id = nextId++
        rows[id] = transaction.copy(id = id)
        lastInsertResult = id
        return id
    }

    override suspend fun findById(id: Long): TransactionEntity? = rows[id]

    override fun observeByState(state: PushState): Flow<List<TransactionEntity>> =
        flowOf(rows.values.filter { it.pushState == state.name })

    override fun observeReviewQueue(
        parsed: PushState,
        failedRetryable: PushState,
        failedPermanent: PushState,
        needsVerify: PushState,
    ): Flow<List<TransactionEntity>> = flowOf(
        rows.values.filter {
            it.pushState in setOf(parsed.name, failedRetryable.name, failedPermanent.name, needsVerify.name)
        }
    )

    override suspend fun peekQueuedOldestFirst(limit: Int, queued: PushState): List<TransactionEntity> =
        rows.values.filter { it.pushState == queued.name }.sortedBy { it.timestamp }.take(limit)

    override suspend fun markSending(ids: List<Long>, now: Long, sending: PushState, queued: PushState): Int = 0

    override suspend fun findSendingByIds(ids: List<Long>, now: Long, sending: PushState): List<TransactionEntity> =
        emptyList()

    override suspend fun markPushed(id: Long, walletRecordId: String, now: Long, pushed: PushState) = Unit

    override suspend fun updateFailedState(id: Long, state: PushState, error: String?, now: Long) = Unit

    override suspend fun findOrphanedSending(olderThanMillis: Long, sending: PushState): List<TransactionEntity> =
        emptyList()

    override suspend fun findNeedsVerify(needsVerify: PushState): List<TransactionEntity> = emptyList()

    override suspend fun findPotentialDuplicate(
        walletAccountId: String,
        amount: String,
        fromTs: Long,
        toTs: Long,
        excludeId: Long,
    ): List<TransactionEntity> = rows.values.filter {
        it.walletAccountId == walletAccountId &&
            it.amount == amount &&
            it.timestamp in fromTs..toTs &&
            it.id != excludeId
    }

    override fun observePushedCount(dayStartMillis: Long, dayEndMillis: Long, pushed: PushState): Flow<Int> =
        flowOf(rows.values.count { it.pushState == pushed.name && it.updatedAt in dayStartMillis..dayEndMillis })

    override suspend fun requeueUnsent(
        id: Long,
        error: String?,
        now: Long,
        queued: PushState,
        sending: PushState,
    ): Int {
        val row = rows[id] ?: return 0
        if (row.pushState != sending.name) return 0
        rows[id] = row.copy(pushState = queued.name, lastError = error, updatedAt = now)
        return 1
    }

    override suspend fun dismissAllReviewable(
        reason: String,
        now: Long,
        dismissed: PushState,
        parsed: PushState,
        failedRetryable: PushState,
        failedPermanent: PushState,
        needsVerify: PushState,
    ): Int {
        val reviewable = setOf(parsed.name, failedRetryable.name, failedPermanent.name, needsVerify.name)
        val targets = rows.filterValues { it.pushState in reviewable }
        targets.forEach { (id, row) ->
            rows[id] = row.copy(pushState = dismissed.name, lastError = reason, updatedAt = now)
        }
        return targets.size
    }

    override fun observeDistinctSources(): Flow<List<TransactionSource>> = flowOf(
        rows.values
            .map { TransactionSource(bankName = it.bankName, accountLast4 = it.accountLast4) }
            .distinct()
    )

    override fun observePendingCount(queued: PushState, sending: PushState): Flow<Int> =
        flowOf(rows.values.count { it.pushState == queued.name || it.pushState == sending.name })

    override suspend fun update(transaction: TransactionEntity) {
        rows[transaction.id] = transaction
    }
}

/** In-memory fake of [AccountMappingDao]. */
private class FakeAccountMappingDao : AccountMappingDao {
    val mappings = mutableListOf<AccountMappingEntity>()
    private var nextId = 1L

    override suspend fun upsert(mapping: AccountMappingEntity): Long {
        mappings.removeAll { it.bankName == mapping.bankName && it.accountLast4 == mapping.accountLast4 }
        val id = if (mapping.id != 0L) mapping.id else nextId++
        mappings += mapping.copy(id = id)
        return id
    }

    override suspend fun update(mapping: AccountMappingEntity) {
        val index = mappings.indexOfFirst { it.id == mapping.id }
        if (index >= 0) mappings[index] = mapping
    }

    override suspend fun delete(mapping: AccountMappingEntity) {
        mappings.removeAll { it.id == mapping.id }
    }

    override fun observeAll(): Flow<List<AccountMappingEntity>> = flowOf(mappings.toList())

    override suspend fun findByBankAndLast4(bankName: String, accountLast4: String): AccountMappingEntity? =
        mappings.firstOrNull { it.bankName == bankName && it.accountLast4 == accountLast4 }

    override suspend fun findAnyByBank(bankName: String): AccountMappingEntity? =
        mappings.filter { it.bankName == bankName }
            .minByOrNull { if (it.accountLast4.isEmpty()) 0 else 1 }
}

/** In-memory fake of [CategoryRuleDao]. */
private class FakeCategoryRuleDao : CategoryRuleDao {
    val rules = mutableListOf<CategoryRuleEntity>()
    private var nextId = 1L

    override suspend fun upsert(rule: CategoryRuleEntity): Long {
        val id = if (rule.id != 0L) rule.id else nextId++
        rules += rule.copy(id = id)
        return id
    }

    override suspend fun update(rule: CategoryRuleEntity) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) rules[index] = rule
    }

    override suspend fun delete(rule: CategoryRuleEntity) {
        rules.removeAll { it.id == rule.id }
    }

    override fun observeAllOrdered(): Flow<List<CategoryRuleEntity>> = flowOf(rules.sortedBy { it.priority })

    override suspend fun findApplicableRules(bankName: String): List<CategoryRuleEntity> =
        rules.filter { it.bankName == null || it.bankName == bankName }.sortedBy { it.priority }
}

/** In-memory fake of [UnmatchedSmsDao]. */
private class FakeUnmatchedSmsDao : UnmatchedSmsDao {
    val rows = mutableListOf<UnmatchedSmsEntity>()
    private var nextId = 1L

    override suspend fun insertIgnore(sms: UnmatchedSmsEntity): Long {
        if (rows.any { it.smsHash == sms.smsHash }) return -1L
        val id = nextId++
        rows += sms.copy(id = id)
        return id
    }

    override suspend fun delete(sms: UnmatchedSmsEntity) {
        rows.removeAll { it.id == sms.id }
    }

    override fun observeAll(): Flow<List<UnmatchedSmsEntity>> = flowOf(rows.toList())

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }
}
