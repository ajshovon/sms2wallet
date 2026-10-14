package me.shovon.sms2wallet.domain.model

import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import me.shovon.bdparser.TransactionType

/**
 * A credit card whose annual fee is waived once it has been used [target] times within one fee
 * year, and the date that fee year starts.
 *
 * The year renews by itself on [startMonth]/[startDay] - usually the card's anniversary - so the
 * user sets it up once rather than re-entering dates every year.
 *
 * A supplementary card is a second card on the same account: its SMS carry its own last 4
 * digits, but the bank counts its purchases towards the main card's waiver. Those cards are
 * listed in [supplementaryLast4s] and counted as part of this one rather than tracked alone.
 */
data class FeeWaiverTracker(
    /** [me.shovon.bdparser.bank.BankParser.getBankName] of the card's issuer. */
    val bankName: String,
    /** Last 4 digits the SMS exposes; empty when the source has none. */
    val accountLast4: String,
    val target: Int,
    val startMonth: Int,
    val startDay: Int,
    /** Last 4 digits of supplementary cards from the same bank, whose purchases count here too. */
    val supplementaryLast4s: List<String> = emptyList(),
) {
    /** Every card whose purchases count towards this waiver: the main card first. */
    val allLast4s: List<String> get() = listOf(accountLast4) + supplementaryLast4s

    /**
     * The fee year [today] falls in, both ends inclusive.
     *
     * A 29 February start falls back to the 28th in years that have no 29th, so the period never
     * skips a year or overlaps the next one.
     */
    fun periodContaining(today: LocalDate): ClosedRange<LocalDate> =
        feeYearContaining(today, startMonth, startDay)

    companion object {
        const val DEFAULT_TARGET = 24
        const val MAX_TARGET = 999

        /**
         * What a bank counts towards a waiver: purchases. Bill payments, refunds and cashback on
         * the card arrive as INCOME and must not inflate the count. `CREDIT` is the parsers'
         * name for a credit-card spend, so it counts too.
         */
        val COUNTED_TYPES: List<TransactionType> = listOf(TransactionType.EXPENSE, TransactionType.CREDIT)

        /** The fee year starting each [startMonth]/[startDay] that [today] falls in. */
        fun feeYearContaining(today: LocalDate, startMonth: Int, startDay: Int): ClosedRange<LocalDate> {
            fun startIn(year: Int): LocalDate {
                val month = YearMonth.of(year, startMonth)
                return month.atDay(minOf(startDay, month.lengthOfMonth()))
            }
            val thisYear = startIn(today.year)
            val start = if (today.isBefore(thisYear)) startIn(today.year - 1) else thisYear
            return start..startIn(start.year + 1).minusDays(1)
        }

        private const val SEPARATOR = '|'
        private const val LIST_SEPARATOR = ','
        private const val BASE_FIELD_COUNT = 5

        /**
         * Serialised for DataStore as `bank|last4|target|month|day`, plus `|sup1,sup2` when the
         * card has supplementary cards.
         *
         * The sixth field is left off entirely when there are none, so a tracker without
         * supplementaries reads the same as one written by 0.9.0 - which also means 0.9.0 can
         * still read it after a downgrade.
         */
        fun encode(tracker: FeeWaiverTracker): String = with(tracker) {
            val fields = listOf(bankName, accountLast4, target, startMonth, startDay) +
                listOfNotNull(supplementaryLast4s.takeIf { it.isNotEmpty() }?.joinToString(LIST_SEPARATOR.toString()))
            fields.joinToString(SEPARATOR.toString())
        }

        /** Null for anything malformed, so one bad entry cannot take the whole list down. */
        fun decode(value: String): FeeWaiverTracker? {
            val parts = value.split(SEPARATOR)
            if (parts.size !in BASE_FIELD_COUNT..BASE_FIELD_COUNT + 1 || parts[0].isBlank()) return null
            val target = parts[2].toIntOrNull()?.takeIf { it in 1..MAX_TARGET } ?: return null
            val month = parts[3].toIntOrNull() ?: return null
            val day = parts[4].toIntOrNull() ?: return null
            // MonthDay accepts 29 February and rejects 31 April, which is exactly the rule here.
            runCatching { MonthDay.of(month, day) }.getOrNull() ?: return null
            val supplementary = parts.getOrNull(BASE_FIELD_COUNT).orEmpty()
                .split(LIST_SEPARATOR)
                .filter { it.isNotBlank() && it != parts[1] }
                .distinct()
            return FeeWaiverTracker(parts[0], parts[1], target, month, day, supplementary)
        }
    }
}

/** How far one tracked card has got in the fee year containing [asOf]. */
data class FeeWaiverProgress(
    val tracker: FeeWaiverTracker,
    val period: ClosedRange<LocalDate>,
    val count: Int,
    val asOf: LocalDate,
)
