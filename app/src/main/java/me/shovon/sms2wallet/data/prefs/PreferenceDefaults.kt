package me.shovon.sms2wallet.data.prefs

import me.shovon.bdparser.bank.BankParser

/**
 * Pure, Android-free default/merge logic for [AppPreferences], pulled out of the DataStore
 * wrapper so it can be exercised directly in JVM unit tests without touching DataStore itself.
 */
object PreferenceDefaults {

    /** Default reminder time: 20:00 (8:00 PM), expressed as minutes since midnight. */
    const val DEFAULT_REMINDER_TIME_MINUTES = 20 * 60

    /** Skip the daily reminder once at least this many transactions were logged today. */
    const val DEFAULT_REMINDER_SUPPRESS_THRESHOLD = 3

    private const val MIN_MINUTES_SINCE_MIDNIGHT = 0
    private const val MAX_MINUTES_SINCE_MIDNIGHT = 23 * 60 + 59

    /**
     * Every parser is enabled out of the box - there is nothing unsafe about merely
     * *recognising* a bank's SMS, only about auto-pushing it (see [defaultAutoPushParserNames]).
     */
    fun defaultEnabledParserNames(allParsers: List<BankParser>): Set<String> =
        allParsers.map { it.getBankName() }.toSet()

    /**
     * Nothing auto-pushes by default. The Wallet API has no idempotency key, so a push cannot
     * be safely undone; requiring an explicit opt-in per bank is the safer default.
     */
    fun defaultAutoPushParserNames(): Set<String> = emptySet()

    /** Filters the full parser catalogue down to the subset the user has enabled by name. */
    fun resolveEnabledParsers(allParsers: List<BankParser>, enabledNames: Set<String>): List<BankParser> =
        allParsers.filter { it.getBankName() in enabledNames }

    /** Clamps a persisted/user-supplied reminder time into the valid 0..1439 range. */
    fun clampReminderTimeMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_MINUTES_SINCE_MIDNIGHT, MAX_MINUTES_SINCE_MIDNIGHT)

    /** True when today's logged-transaction count already meets the suppression threshold. */
    fun shouldSuppressReminder(transactionsLoggedToday: Int, threshold: Int): Boolean =
        transactionsLoggedToday >= threshold
}
