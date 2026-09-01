package me.shovon.sms2wallet.prefs

import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.sms2wallet.data.prefs.PreferenceDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure default/merge logic extracted out of [me.shovon.sms2wallet.data.prefs.AppPreferences]
 * so it's testable without DataStore/Android.
 */
class PreferenceDefaultsTest {

    private val allParsers = BankParserFactory.getAllParsers()

    @Test
    fun `all parsers are enabled by default`() {
        val defaults = PreferenceDefaults.defaultEnabledParserNames(allParsers)

        assertEquals(allParsers.size, defaults.size)
        assertEquals(9, defaults.size)
        allParsers.forEach { parser -> assertTrue(parser.getBankName() in defaults) }
    }

    @Test
    fun `no parsers auto-push by default`() {
        assertTrue(PreferenceDefaults.defaultAutoPushParserNames().isEmpty())
    }

    @Test
    fun `resolveEnabledParsers filters by bank name`() {
        val enabledNames = setOf("bKash", "Mutual Trust Bank")

        val resolved = PreferenceDefaults.resolveEnabledParsers(allParsers, enabledNames)

        assertEquals(2, resolved.size)
        assertTrue(resolved.any { it.getBankName() == "bKash" })
        assertTrue(resolved.any { it.getBankName() == "Mutual Trust Bank" })
    }

    @Test
    fun `resolveEnabledParsers returns empty list when nothing is enabled`() {
        assertTrue(PreferenceDefaults.resolveEnabledParsers(allParsers, emptySet()).isEmpty())
    }

    @Test
    fun `clampReminderTimeMinutes keeps in-range values untouched`() {
        assertEquals(0, PreferenceDefaults.clampReminderTimeMinutes(0))
        assertEquals(1439, PreferenceDefaults.clampReminderTimeMinutes(1439))
        assertEquals(1200, PreferenceDefaults.clampReminderTimeMinutes(1200))
    }

    @Test
    fun `clampReminderTimeMinutes clamps out-of-range values`() {
        assertEquals(0, PreferenceDefaults.clampReminderTimeMinutes(-30))
        assertEquals(1439, PreferenceDefaults.clampReminderTimeMinutes(5000))
    }

    @Test
    fun `default reminder time is 8pm`() {
        assertEquals(1200, PreferenceDefaults.DEFAULT_REMINDER_TIME_MINUTES)
    }

    @Test
    fun `shouldSuppressReminder is threshold-inclusive`() {
        assertFalse(PreferenceDefaults.shouldSuppressReminder(2, threshold = 3))
        assertTrue(PreferenceDefaults.shouldSuppressReminder(3, threshold = 3))
        assertTrue(PreferenceDefaults.shouldSuppressReminder(5, threshold = 3))
    }
}
