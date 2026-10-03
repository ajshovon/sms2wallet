package me.shovon.sms2wallet.presentation.screens.activity

import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import me.shovon.sms2wallet.presentation.model.PushLogEntryUiState
import me.shovon.sms2wallet.presentation.model.PushLogStatus
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityScreenFilterTest {

    private val zone = ZoneId.of("Asia/Dhaka")

    private fun createEntry(
        id: String,
        timestamp: Long,
        dayLabel: String = "",
        dateTimeLabel: String = ""
    ): PushLogEntryUiState = PushLogEntryUiState(
        id = id,
        merchant = "Test Merchant",
        amount = BigDecimal("100.00"),
        direction = TransactionDirection.EXPENSE,
        status = PushLogStatus.SUCCESS,
        timeLabel = "10:00 AM",
        dateTimeLabel = dateTimeLabel,
        dayLabel = dayLabel,
        timestamp = timestamp
    )

    @Test
    fun `matchesDateFilter correctly matches today`() {
        val now = ZonedDateTime.now(zone).toInstant().toEpochMilli()
        val entryToday = createEntry("1", now, "Today", "Today, 10:00 AM")

        assertTrue(matchesDateFilter(entryToday, ActivityDateFilter.ALL, zone))
        assertTrue(matchesDateFilter(entryToday, ActivityDateFilter.TODAY, zone))
        assertFalse(matchesDateFilter(entryToday, ActivityDateFilter.YESTERDAY, zone))
        assertTrue(matchesDateFilter(entryToday, ActivityDateFilter.THIS_WEEK, zone))
        assertFalse(matchesDateFilter(entryToday, ActivityDateFilter.OLDER, zone))
    }

    @Test
    fun `matchesDateFilter correctly matches yesterday`() {
        val yesterday = ZonedDateTime.now(zone).minusDays(1).toInstant().toEpochMilli()
        val entryYesterday = createEntry("2", yesterday, "Yesterday", "Yesterday, 9:00 AM")

        assertTrue(matchesDateFilter(entryYesterday, ActivityDateFilter.ALL, zone))
        assertFalse(matchesDateFilter(entryYesterday, ActivityDateFilter.TODAY, zone))
        assertTrue(matchesDateFilter(entryYesterday, ActivityDateFilter.YESTERDAY, zone))
        assertTrue(matchesDateFilter(entryYesterday, ActivityDateFilter.THIS_WEEK, zone))
        assertFalse(matchesDateFilter(entryYesterday, ActivityDateFilter.OLDER, zone))
    }

    @Test
    fun `matchesDateFilter correctly matches this week and older`() {
        val fourDaysAgo = ZonedDateTime.now(zone).minusDays(4).toInstant().toEpochMilli()
        val entryThisWeek = createEntry("3", fourDaysAgo)

        assertTrue(matchesDateFilter(entryThisWeek, ActivityDateFilter.ALL, zone))
        assertFalse(matchesDateFilter(entryThisWeek, ActivityDateFilter.TODAY, zone))
        assertFalse(matchesDateFilter(entryThisWeek, ActivityDateFilter.YESTERDAY, zone))
        assertTrue(matchesDateFilter(entryThisWeek, ActivityDateFilter.THIS_WEEK, zone))
        assertFalse(matchesDateFilter(entryThisWeek, ActivityDateFilter.OLDER, zone))

        val tenDaysAgo = ZonedDateTime.now(zone).minusDays(10).toInstant().toEpochMilli()
        val entryOlder = createEntry("4", tenDaysAgo)

        assertTrue(matchesDateFilter(entryOlder, ActivityDateFilter.ALL, zone))
        assertFalse(matchesDateFilter(entryOlder, ActivityDateFilter.TODAY, zone))
        assertFalse(matchesDateFilter(entryOlder, ActivityDateFilter.YESTERDAY, zone))
        assertFalse(matchesDateFilter(entryOlder, ActivityDateFilter.THIS_WEEK, zone))
        assertTrue(matchesDateFilter(entryOlder, ActivityDateFilter.OLDER, zone))
    }
}
