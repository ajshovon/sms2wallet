package me.shovon.sms2wallet.presentation.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders epoch-milli timestamps as the short, human labels the UI uses ("Today", "10:24 AM",
 * "2 minutes ago").
 *
 * Everything resolves against the device's current zone at call time rather than a cached
 * one, so a label computed after the user crosses a timezone (or after DST) is still correct.
 */
object TimeFormatter {

    private val timeOfDay: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dayAndMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

    /** "Today" / "Yesterday" / "Mon, 12 Aug" - the Review-queue day-group header. */
    fun dayLabel(timestampMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (timestampMillis <= 0L) return ""
        val date = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dayAndMonth)
            else -> date.format(dayMonthYear)
        }
    }

    /** "10:24 AM". */
    fun timeLabel(timestampMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (timestampMillis <= 0L) return ""
        return Instant.ofEpochMilli(timestampMillis).atZone(zone).format(timeOfDay)
    }

    /** "Today, 11:02 AM" - used where a row is not already under a day header. */
    fun dayAndTimeLabel(timestampMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (timestampMillis <= 0L) return ""
        val day = dayLabel(timestampMillis, zone)
        val time = timeLabel(timestampMillis, zone)
        return if (day.isNotEmpty() && time.isNotEmpty()) "$day, $time" else day.ifEmpty { time }
    }

    /**
     * Coarse relative label for "last sync": "just now", "2 minutes ago", "3 hours ago",
     * "5 days ago". Returns null for a zero/absent timestamp so callers can render their own
     * "never" copy rather than showing a bogus 1970 date.
     */
    fun relativeLabel(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String? {
        if (timestampMillis <= 0L) return null
        val elapsed = nowMillis - timestampMillis
        if (elapsed < 0L) return "just now"
        val minutes = elapsed / 60_000L
        if (minutes < 1L) return "just now"
        if (minutes < 60L) return "$minutes minute${plural(minutes)} ago"
        val hours = minutes / 60L
        if (hours < 24L) return "$hours hour${plural(hours)} ago"
        val days = hours / 24L
        return "$days day${plural(days)} ago"
    }

    private fun plural(value: Long): String = if (value == 1L) "" else "s"
}
