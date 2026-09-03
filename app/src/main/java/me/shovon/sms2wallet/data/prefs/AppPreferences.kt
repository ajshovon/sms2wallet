package me.shovon.sms2wallet.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.shovon.bdparser.bank.BankParser
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode
import me.shovon.bdparser.bank.BankParserFactory
import me.shovon.bdparser.bank.BankParserRegistry

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences"
)

/**
 * User-configurable app settings, backed by Jetpack DataStore Preferences.
 *
 * This is the seam for the (future) parser enable/disable and auto-push settings screens:
 * nothing here talks to Room or the network, it only reads/writes flags that other layers
 * (the SMS ingest pipeline, a push worker, a reminder worker) consult.
 */
class AppPreferences(
    @ApplicationContext private val context: Context
) {

    private val dataStore get() = context.appPreferencesDataStore

    private val safeData: Flow<Preferences>
        get() = dataStore.data.catch { emit(emptyPreferences()) }

    // ------------------------------------------------------------------
    // Enabled / auto-push parsers
    // ------------------------------------------------------------------

    /** Bank names (as returned by [BankParser.getBankName]) whose SMS should be parsed at all. */
    val enabledParserNames: Flow<Set<String>> = safeData.map { prefs ->
        prefs[ENABLED_PARSERS_KEY] ?: PreferenceDefaults.defaultEnabledParserNames(
            BankParserFactory.getAllParsers()
        )
    }

    /**
     * Bank names whose *parsed* transactions may be pushed to the Wallet API without manual
     * confirmation. Empty by default - see [PreferenceDefaults.defaultAutoPushParserNames].
     */
    val autoPushParserNames: Flow<Set<String>> = safeData.map { prefs ->
        prefs[AUTO_PUSH_PARSERS_KEY] ?: PreferenceDefaults.defaultAutoPushParserNames()
    }

    suspend fun setEnabledParserNames(names: Set<String>) {
        dataStore.edit { prefs -> prefs[ENABLED_PARSERS_KEY] = names }
    }

    suspend fun setAutoPushParserNames(names: Set<String>) {
        dataStore.edit { prefs -> prefs[AUTO_PUSH_PARSERS_KEY] = names }
    }

    /** Resolves the currently-enabled parsers against the full parser catalogue. */
    suspend fun enabledParsers(): List<BankParser> {
        val enabledNames = enabledParserNames.first()
        return PreferenceDefaults.resolveEnabledParsers(BankParserFactory.getAllParsers(), enabledNames)
    }

    /** A [BankParserRegistry] scoped to the currently-enabled parsers only. */
    suspend fun enabledParserRegistry(): BankParserRegistry = BankParserRegistry(enabledParsers())

    // ------------------------------------------------------------------
    // Daily reminder
    // ------------------------------------------------------------------

    val reminderEnabled: Flow<Boolean> = safeData.map { it[REMINDER_ENABLED_KEY] ?: true }

    /** Minutes since midnight, always in 0..1439 (see [PreferenceDefaults.clampReminderTimeMinutes]). */
    val reminderTimeMinutes: Flow<Int> = safeData.map { prefs ->
        PreferenceDefaults.clampReminderTimeMinutes(
            prefs[REMINDER_TIME_MINUTES_KEY] ?: PreferenceDefaults.DEFAULT_REMINDER_TIME_MINUTES
        )
    }

    /** Skip the reminder once at least this many transactions were logged today. */
    val reminderSuppressThreshold: Flow<Int> = safeData.map {
        it[REMINDER_SUPPRESS_THRESHOLD_KEY] ?: PreferenceDefaults.DEFAULT_REMINDER_SUPPRESS_THRESHOLD
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REMINDER_ENABLED_KEY] = enabled }
    }

    suspend fun setReminderTimeMinutes(minutes: Int) {
        val clamped = PreferenceDefaults.clampReminderTimeMinutes(minutes)
        dataStore.edit { prefs -> prefs[REMINDER_TIME_MINUTES_KEY] = clamped }
    }

    suspend fun setReminderSuppressThreshold(threshold: Int) {
        dataStore.edit { prefs -> prefs[REMINDER_SUPPRESS_THRESHOLD_KEY] = threshold }
    }

    // ------------------------------------------------------------------
    // SMS backfill scan state
    // ------------------------------------------------------------------

    /** High-water mark (epoch millis) for the SMS backfill scan; SMS at/after this are re-scanned. */
    val lastScannedTimestamp: Flow<Long> = safeData.map { it[LAST_SCANNED_TIMESTAMP_KEY] ?: 0L }

    /** When true, the next backfill ignores [lastScannedTimestamp] and scans the whole inbox. */
    val scanAllTime: Flow<Boolean> = safeData.map { it[SCAN_ALL_TIME_KEY] ?: false }

    // ---- Appearance ---------------------------------------------------------------

    /** The user's chosen theme, defaulting to following the system. */
    val themeMode: Flow<ThemeMode> = safeData.map { ThemeMode.fromName(it[THEME_MODE_KEY]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    /** The seed colour the palette is generated from. */
    val accentColor: Flow<AccentColor> = safeData.map { AccentColor.fromName(it[ACCENT_COLOR_KEY]) }

    suspend fun setAccentColor(accent: AccentColor) {
        dataStore.edit { it[ACCENT_COLOR_KEY] = accent.name }
    }

    // ---- First-run coaching -------------------------------------------------------

    /**
     * Whether the user has ever acted on a review row (pushed or dismissed).
     *
     * Gates the swipe hint on the review queue. An instruction that never goes away stops being
     * help and becomes permanent furniture, so it is shown until the gesture has been used once
     * and then retired.
     */
    val hasActedOnReviewQueue: Flow<Boolean> = safeData.map { it[HAS_ACTED_ON_REVIEW_KEY] ?: false }

    suspend fun setHasActedOnReviewQueue() {
        dataStore.edit { it[HAS_ACTED_ON_REVIEW_KEY] = true }
    }

    // ---- Wallet catalogue (accounts + categories) --------------------------------

    /**
     * When the Wallet account/category catalogue was last pulled from the API, or 0 if never.
     *
     * Tracked separately from [lastScannedTimestamp], which is about the SMS inbox: the two
     * answer different questions ("is my Wallet list current?" vs "have my messages been read?")
     * and go stale for entirely different reasons.
     */
    val lastCatalogueSyncAt: Flow<Long> = safeData.map { it[LAST_CATALOGUE_SYNC_KEY] ?: 0L }

    suspend fun setLastCatalogueSyncAt(timestamp: Long) {
        dataStore.edit { it[LAST_CATALOGUE_SYNC_KEY] = timestamp }
    }

    suspend fun setLastScannedTimestamp(timestamp: Long) {
        dataStore.edit { prefs -> prefs[LAST_SCANNED_TIMESTAMP_KEY] = timestamp }
    }

    suspend fun setScanAllTime(scanAllTime: Boolean) {
        dataStore.edit { prefs -> prefs[SCAN_ALL_TIME_KEY] = scanAllTime }
    }

    private companion object {
        val ENABLED_PARSERS_KEY = stringSetPreferencesKey("enabled_parsers")
        val AUTO_PUSH_PARSERS_KEY = stringSetPreferencesKey("auto_push_parsers")
        val REMINDER_ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME_MINUTES_KEY = intPreferencesKey("reminder_time_minutes")
        val REMINDER_SUPPRESS_THRESHOLD_KEY = intPreferencesKey("reminder_suppress_threshold")
        val LAST_SCANNED_TIMESTAMP_KEY = longPreferencesKey("last_scanned_timestamp")
        val SCAN_ALL_TIME_KEY = booleanPreferencesKey("scan_all_time")
        val LAST_CATALOGUE_SYNC_KEY = longPreferencesKey("last_catalogue_sync_at")
        val HAS_ACTED_ON_REVIEW_KEY = booleanPreferencesKey("has_acted_on_review_queue")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    }
}
