package me.shovon.sms2wallet.presentation.model

import me.shovon.bdparser.bank.BankParserFactory
import java.math.BigDecimal

/**
 * Synthetic sample data for `@Preview`s ONLY.
 *
 * Every screen is now driven by its ViewModel off the real data layer; nothing here reaches a
 * running app. Keep it that way - a screen that falls back to this at runtime shows the user
 * fabricated transactions and balances as though they were their own.
 *
 * Every value is fabricated: no real merchant names, account numbers, phone numbers, or
 * amounts tied to a person.
 */
object SampleData {

    val dashboard = DashboardUiState(
        pushedToday = 4,
        pushedThisWeek = 21,
        pendingReviewCount = 3,
        lastSyncLabel = "2 minutes ago",
        tokenHealth = TokenHealth.VALID,
        rateLimit = RateLimitUiState(used = 42, limit = 300, windowLabel = "this hour")
    )

    val reviewQueue = ReviewQueueUiState(
        groups = listOf(
            ReviewQueueDayGroup(
                dayLabel = "Today",
                transactions = listOf(
                    ReviewTransactionUiState(
                        id = "txn-1",
                        merchant = "SAMPLE SHOP BD",
                        amount = BigDecimal("650.00"),
                        direction = TransactionDirection.EXPENSE,
                        providerName = "bKash",
                        accountLast4 = "0000",
                        timeLabel = "10:24 AM"
                    ),
                    ReviewTransactionUiState(
                        id = "txn-2",
                        merchant = "GROCERY MART",
                        amount = BigDecimal("1250.50"),
                        direction = TransactionDirection.EXPENSE,
                        providerName = "City Bank",
                        accountLast4 = "1234",
                        timeLabel = "9:02 AM",
                        isSuspectedDuplicate = true
                    ),
                    ReviewTransactionUiState(
                        id = "txn-3",
                        merchant = "SALARY DISBURSEMENT",
                        amount = BigDecimal("45000.00"),
                        direction = TransactionDirection.INCOME,
                        providerName = "BRAC Bank",
                        accountLast4 = "5678",
                        timeLabel = "8:00 AM",
                        needsVerification = true
                    )
                )
            ),
            ReviewQueueDayGroup(
                dayLabel = "Yesterday",
                transactions = listOf(
                    ReviewTransactionUiState(
                        id = "txn-4",
                        merchant = "SAMPLE PHARMACY",
                        amount = BigDecimal("340.00"),
                        direction = TransactionDirection.EXPENSE,
                        providerName = "Nagad",
                        accountLast4 = "9012",
                        timeLabel = "6:45 PM"
                    )
                )
            )
        )
    )

    val emptyReviewQueue = ReviewQueueUiState(groups = emptyList())

    val transactionDetail = TransactionDetailUiState(
        id = "txn-1",
        merchant = "SAMPLE SHOP BD",
        amountText = "650.00",
        direction = TransactionDirection.EXPENSE,
        category = "Groceries",
        availableCategories = listOf("Groceries", "Dining", "Transport", "Bills", "Shopping", "Other"),
        accountName = "Cash Wallet",
        availableAccounts = listOf("Cash Wallet", "bKash Wallet", "City Bank Account"),
        note = "",
        providerName = "bKash"
    )

    val addCashExpense = TransactionDetailUiState(
        id = null,
        direction = TransactionDirection.EXPENSE,
        availableCategories = listOf("Groceries", "Dining", "Transport", "Bills", "Shopping", "Other"),
        accountName = "Cash Wallet",
        availableAccounts = listOf("Cash Wallet", "bKash Wallet", "City Bank Account")
    )

    val walletConnectionConnected = WalletConnectionUiState(
        tokenInput = "sample-token-••••••••",
        status = ConnectionStatus.Success
    )

    val walletConnectionSyncing = WalletConnectionUiState(
        tokenInput = "sample-token-••••••••",
        status = ConnectionStatus.Syncing(retryInMinutes = 5)
    )

    /**
     * Sample per-provider auto-push/mapping overrides, keyed by [me.shovon.bdparser.bank.BankParser.getBankName].
     * Providers not listed here default to enabled, auto-push off, unmapped.
     */
    private val parserSampleOverrides: Map<String, ParserSettingUiState> = mapOf(
        "bKash" to ParserSettingUiState("bKash", isEnabled = true, isAutoPushEnabled = true, mappedAccountName = "bKash Wallet"),
        "Rocket (DBBL)" to ParserSettingUiState("Rocket (DBBL)", isEnabled = false, isAutoPushEnabled = false, mappedAccountName = null),
        "Upay" to ParserSettingUiState("Upay", isEnabled = true, isAutoPushEnabled = false, mappedAccountName = "Upay Wallet"),
        "City Bank" to ParserSettingUiState("City Bank", isEnabled = true, isAutoPushEnabled = true, mappedAccountName = "City Bank Account"),
        "BRAC Bank" to ParserSettingUiState("BRAC Bank", isEnabled = true, isAutoPushEnabled = false, mappedAccountName = "BRAC Bank Account"),
        "Eastern Bank" to ParserSettingUiState("Eastern Bank", isEnabled = false, isAutoPushEnabled = false, mappedAccountName = null)
    )

    /** Provider list sourced from the real parser registry - see class KDoc for the exception this relies on. */
    private val parserSettingsFromRegistry: List<ParserSettingUiState> =
        BankParserFactory.getAllParsers().map { parser ->
            val bankName = parser.getBankName()
            parserSampleOverrides[bankName] ?: ParserSettingUiState(providerName = bankName)
        }

    val settings = SettingsUiState(
        walletConnection = walletConnectionConnected,
        parserSettings = parserSettingsFromRegistry,
        accountMappings = listOf(
            AccountMappingRowUiState(
                sourceId = "src-bkash",
                sourceLabel = "bKash •••• 0000",
                mappedWalletAccountName = "bKash Wallet",
                availableWalletAccountNames = listOf("Cash Wallet", "bKash Wallet", "City Bank Account")
            ),
            AccountMappingRowUiState(
                sourceId = "src-nagad",
                sourceLabel = "Nagad •••• 1234",
                mappedWalletAccountName = null,
                availableWalletAccountNames = listOf("Cash Wallet", "bKash Wallet", "City Bank Account")
            )
        ),
        reminders = ReminderSettingsUiState(
            isEnabled = true,
            hourOfDay = 21,
            minute = 0,
            skipIfAlreadyLoggedCount = 3
        )
    )

    val activity = ActivityUiState(
        logs = listOf(
            PushLogEntryUiState(
                id = "log-1",
                merchant = "SAMPLE SHOP BD",
                amount = BigDecimal("650.00"),
                direction = TransactionDirection.EXPENSE,
                status = PushLogStatus.SUCCESS,
                timeLabel = "10:25 AM"
            ),
            PushLogEntryUiState(
                id = "log-2",
                merchant = "GROCERY MART",
                amount = BigDecimal("1250.50"),
                direction = TransactionDirection.EXPENSE,
                status = PushLogStatus.FAILED,
                timeLabel = "9:05 AM",
                errorMessage = "Wallet API returned 401 Unauthorized"
            ),
            PushLogEntryUiState(
                id = "log-3",
                merchant = "SALARY DISBURSEMENT",
                amount = BigDecimal("45000.00"),
                direction = TransactionDirection.INCOME,
                status = PushLogStatus.RETRYING,
                timeLabel = "8:01 AM"
            )
        )
    )

    val emptyActivity = ActivityUiState(logs = emptyList())

    val unmatchedSms = UnmatchedSmsScreenUiState(
        items = listOf(
            UnmatchedSmsUiState(
                id = "sms-1",
                sender = "01700000000",
                bodyPreview = "Your OTP for login is 123456. Do not share this with anyone.",
                receivedAtLabel = "Today, 11:02 AM"
            ),
            UnmatchedSmsUiState(
                id = "sms-2",
                sender = "16247",
                bodyPreview = "Dear customer, your bill payment of BDT 500 is due on 15th.",
                receivedAtLabel = "Yesterday, 4:30 PM"
            )
        )
    )

    val emptyUnmatchedSms = UnmatchedSmsScreenUiState(items = emptyList())

    val parserPlayground = ParserPlaygroundUiState(
        senderInput = "bKash",
        bodyInput = "You have received Tk 650.00 from SAMPLE SHOP BD. Fee Tk 0.00. Balance Tk 12,340.00. TrxID ABC1D2E3F4 at 01/09/2026 10:24",
        hasRun = true,
        results = listOf(
            ParserMatchResultUiState(
                providerName = "bKash",
                matched = true,
                extractedFields = listOf(
                    ExtractedFieldUiState("Amount", "৳650.00"),
                    ExtractedFieldUiState("Direction", "Income"),
                    ExtractedFieldUiState("Merchant", "SAMPLE SHOP BD"),
                    ExtractedFieldUiState("Balance", "৳12,340.00"),
                    ExtractedFieldUiState("Transaction ID", "ABC1D2E3F4")
                )
            ),
            ParserMatchResultUiState(
                providerName = "Nagad",
                matched = false,
                failureReason = "Sender does not match known Nagad patterns"
            )
        )
    )
}
