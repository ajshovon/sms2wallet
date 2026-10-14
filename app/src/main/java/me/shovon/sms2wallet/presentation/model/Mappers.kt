package me.shovon.sms2wallet.presentation.model

import java.math.BigDecimal
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import me.shovon.bdparser.TransactionType
import me.shovon.sms2wallet.data.local.dao.PushLogWithTransaction
import me.shovon.sms2wallet.data.local.entity.TransactionEntity
import me.shovon.sms2wallet.data.local.entity.UnmatchedSmsEntity
import me.shovon.sms2wallet.domain.model.FeeWaiverProgress
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker
import me.shovon.sms2wallet.domain.model.PushState
import me.shovon.sms2wallet.presentation.util.TimeFormatter

/**
 * Data-layer -> UI-state mapping for the presentation layer.
 *
 * These live on the presentation side on purpose: the data layer stays unaware of how anything
 * is displayed, and every string the user reads (day headers, time labels, masked account
 * hints) is produced in exactly one place.
 */

/** Longest preview of an unmatched SMS body shown before eliding. */
private const val SMS_PREVIEW_MAX_CHARS = 160

/**
 * `CREDIT` is a *credit-card spend* in `:bd-sms-parsers` (see `BankParser.extractAvailableLimit`),
 * not money coming in - so only `INCOME` is treated as income and everything else, including
 * TRANSFER and INVESTMENT, reduces the balance and renders as an expense.
 */
fun TransactionType.toDirection(): TransactionDirection =
    if (this == TransactionType.INCOME) TransactionDirection.INCOME else TransactionDirection.EXPENSE

/** Same mapping, from the stored enum *name*; an unrecognised name degrades to EXPENSE. */
fun directionFromTypeName(typeName: String?): TransactionDirection {
    val type = typeName?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
    return type?.toDirection() ?: TransactionDirection.EXPENSE
}

/** Stored amount is a plain string to avoid float drift; an unparseable value degrades to zero. */
fun parseStoredAmount(amount: String?): BigDecimal =
    amount?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: BigDecimal.ZERO

fun TransactionEntity.toReviewUiState(
    categoryName: String? = null,
    accountName: String? = null,
): ReviewTransactionUiState = ReviewTransactionUiState(
    id = id.toString(),
    // A parser that could not name the counterparty still has to render as something the user
    // can recognise, so fall back to the bank rather than showing an empty card.
    merchant = merchant?.takeIf { it.isNotBlank() } ?: bankName,
    amount = parseStoredAmount(amount),
    direction = directionFromTypeName(type),
    providerName = bankName,
    accountLast4 = accountLast4?.takeIf { it.isNotBlank() } ?: "----",
    timeLabel = TimeFormatter.timeLabel(timestamp),
    category = categoryName,
    accountName = accountName,
    isSuspectedDuplicate = suspectedDuplicateOfId != null,
    needsVerification = pushState == PushState.NEEDS_VERIFY.name,
)

/**
 * Groups newest-first transactions into day buckets. Relies on the DAO already ordering by
 * timestamp DESC, so a [LinkedHashMap] preserves that order for both groups and rows.
 */
fun List<TransactionEntity>.toReviewQueueGroups(
    categoriesById: Map<String, String> = emptyMap(),
    accountsById: Map<String, String> = emptyMap(),
): List<ReviewQueueDayGroup> =
    groupByTo(LinkedHashMap()) { TimeFormatter.dayLabel(it.timestamp) }
        .map { (dayLabel, rows) ->
            ReviewQueueDayGroup(
                dayLabel = dayLabel,
                transactions = rows.map { row ->
                    row.toReviewUiState(
                        categoryName = row.walletCategoryId?.let { categoriesById[it] },
                        accountName = row.walletAccountId?.let { accountsById[it] }
                    )
                }
            )
        }

fun UnmatchedSmsEntity.toUiState(): UnmatchedSmsUiState = UnmatchedSmsUiState(
    id = id.toString(),
    sender = sender,
    bodyPreview = body.take(SMS_PREVIEW_MAX_CHARS) + if (body.length > SMS_PREVIEW_MAX_CHARS) "..." else "",
    receivedAtLabel = TimeFormatter.dayAndTimeLabel(timestamp),
)

/**
 * A log row's status comes from the *transaction's current* push state where one exists, so a
 * failed attempt that has since been requeued shows as retrying rather than permanently failed.
 * Rows whose transaction is gone fall back to the recorded success flag.
 */
fun PushLogWithTransaction.toUiState(): PushLogEntryUiState {
    val state = pushState?.let { runCatching { PushState.valueOf(it) }.getOrNull() }
    val status = when {
        state == PushState.PUSHED -> PushLogStatus.SUCCESS
        state == PushState.QUEUED || state == PushState.SENDING -> PushLogStatus.RETRYING
        state == PushState.FAILED_RETRYABLE || state == PushState.FAILED_PERMANENT -> PushLogStatus.FAILED
        state == PushState.NEEDS_VERIFY -> PushLogStatus.PENDING
        success -> PushLogStatus.SUCCESS
        else -> PushLogStatus.FAILED
    }
    return PushLogEntryUiState(
        id = id.toString(),
        transactionId = transactionId,
        merchant = merchant?.takeIf { it.isNotBlank() } ?: "(transaction removed)",
        amount = parseStoredAmount(amount),
        direction = directionFromTypeName(type),
        status = status,
        timeLabel = TimeFormatter.timeLabel(createdAt),
        dateTimeLabel = TimeFormatter.dayAndTimeLabel(createdAt),
        dayLabel = TimeFormatter.dayLabel(createdAt),
        timestamp = createdAt,
        errorMessage = message?.takeIf { status == PushLogStatus.FAILED },
    )
}

/** Editable detail state for one stored transaction; [availableAccounts]/[availableCategories] come from the Wallet cache. */
fun TransactionEntity.toDetailUiState(
    availableAccounts: List<String>,
    availableCategories: List<String>,
    accountName: String,
    categoryName: String,
): TransactionDetailUiState = TransactionDetailUiState(
    id = id.toString(),
    merchant = merchant.orEmpty(),
    amountText = parseStoredAmount(amount).toPlainString(),
    direction = directionFromTypeName(type),
    category = categoryName,
    availableCategories = availableCategories,
    accountName = accountName,
    availableAccounts = availableAccounts,
    note = reference.orEmpty(),
    providerName = bankName,
    sourceSummary = buildString {
        append(bankName)
        if (!accountLast4.isNullOrBlank()) {
            append(" •••• ")
            append(accountLast4)
        }
        append(" • ")
        append(TimeFormatter.dayAndTimeLabel(timestamp))
    },
    smsPreview = smsBody.takeIf { it.isNotBlank() },
    isSuspectedDuplicate = suspectedDuplicateOfId != null,
    needsVerification = pushState == PushState.NEEDS_VERIFY.name,
)

/** Shortens a bank name the way every source label shows it; only MTB's is long enough to need it. */
fun displayBankName(bank: String): String = when {
    bank.equals("Mutual Trust Bank", ignoreCase = true) ||
        bank.equals("Mutual Trust Bank Limited", ignoreCase = true) ||
        bank.equals("Mutual Trust Bank Ltd", ignoreCase = true) -> "MTB"
    else -> bank
}

/** Labels a card exactly as Settings → Account mapping does, so the two screens agree. */
fun cardLabel(bankName: String, accountLast4: String?): String {
    val displayBank = displayBankName(bankName)
    return if (accountLast4.isNullOrBlank()) displayBank else "$displayBank •••• $accountLast4"
}

/** The `"bank|last4"` key used for a card in UI callbacks, matching account mapping's. */
fun cardSourceId(bankName: String, accountLast4: String?): String = "$bankName|${accountLast4.orEmpty()}"

private val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val dayAndMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * One dashboard row for a tracked card. The status line answers the question the user actually
 * has - "how many more, and by when?" - rather than restating the count.
 */
fun FeeWaiverProgress.toUiState(): FeeWaiverProgressUiState {
    val remaining = (tracker.target - count).coerceAtLeast(0)
    val status = if (remaining == 0) {
        "Target reached · resets ${period.endInclusive.plusDays(1).format(fullDate)}"
    } else {
        // Counting today, so the last day of the fee year reads "1 day left", not "0 days".
        val daysLeft = ChronoUnit.DAYS.between(asOf, period.endInclusive) + 1
        "$remaining more by ${period.endInclusive.format(fullDate)} · $daysLeft day${if (daysLeft == 1L) "" else "s"} left"
    }
    return FeeWaiverProgressUiState(
        cardLabel = cardLabel(tracker.bankName, tracker.accountLast4),
        count = count,
        target = tracker.target,
        statusLabel = status,
        // The count already includes these cards; naming them is what makes a total that is
        // higher than the main card's own SMS explain itself.
        supplementaryLabel = tracker.supplementaryLast4s.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "Includes supplementary ") { "•••• $it" },
    )
}

/** One row in Settings → Card fee waivers; the two lists feed its editor. */
fun FeeWaiverTracker.toSettingsUiState(
    supplementaryOptions: List<SupplementaryCardUiState> = emptyList(),
    trackedSeparatelyLabels: List<String> = emptyList(),
): FeeWaiverTrackerUiState = FeeWaiverTrackerUiState(
    sourceId = cardSourceId(bankName, accountLast4),
    cardLabel = cardLabel(bankName, accountLast4),
    target = target,
    startMonth = startMonth,
    startDay = startDay,
    summary = buildString {
        append("$target purchases per fee year · starts ${MonthDay.of(startMonth, startDay).format(dayAndMonth)}")
        if (supplementaryLast4s.isNotEmpty()) append(" · +${supplementaryLast4s.size} supplementary")
    },
    supplementaryLast4s = supplementaryLast4s,
    supplementaryOptions = supplementaryOptions,
    trackedSeparatelyLabels = trackedSeparatelyLabels,
)
