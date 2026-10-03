package me.shovon.sms2wallet.presentation.util

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatterTest {

    private val zone = ZoneId.of("Asia/Dhaka")

    @Test
    fun `dayLabel returns Today for today timestamp`() {
        val now = ZonedDateTime.now(zone).toInstant().toEpochMilli()
        assertEquals("Today", TimeFormatter.dayLabel(now, zone))
    }

    @Test
    fun `dayLabel returns Yesterday for yesterday timestamp`() {
        val yesterday = ZonedDateTime.now(zone).minusDays(1).toInstant().toEpochMilli()
        assertEquals("Yesterday", TimeFormatter.dayLabel(yesterday, zone))
    }

    @Test
    fun `dayLabel returns empty string for zero or negative timestamp`() {
        assertEquals("", TimeFormatter.dayLabel(0L, zone))
        assertEquals("", TimeFormatter.dayLabel(-100L, zone))
    }

    @Test
    fun `dayAndTimeLabel combines day and time`() {
        val now = ZonedDateTime.now(zone).toInstant().toEpochMilli()
        val label = TimeFormatter.dayAndTimeLabel(now, zone)
        assertTrue("Expected label to start with Today, but was $label", label.startsWith("Today, "))
    }

    @Test
    fun `dayAndTimeLabel returns empty string for zero timestamp`() {
        assertEquals("", TimeFormatter.dayAndTimeLabel(0L, zone))
    }
}
