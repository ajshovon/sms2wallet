package me.shovon.sms2wallet.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import me.shovon.bdparser.bank.BankParser
import me.shovon.sms2wallet.data.local.dao.AccountMappingDao
import me.shovon.sms2wallet.data.local.dao.CategoryRuleDao
import me.shovon.sms2wallet.data.local.entity.AccountMappingEntity
import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.prefs.SecureTokenStore

/**
 * Thin wrapper over [AppPreferences], [SecureTokenStore], [AccountMappingDao] and
 * [CategoryRuleDao] for the (future) settings UI.
 *
 * Deliberately never exposes the decrypted Wallet token itself, only [hasToken] - a settings
 * screen can show/offer-to-clear the stored token without the plaintext value ever passing
 * through a Compose state holder, a ViewModel log line, or anywhere else it could leak.
 */
class SettingsRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val secureTokenStore: SecureTokenStore,
    private val accountMappingDao: AccountMappingDao,
    private val categoryRuleDao: CategoryRuleDao,
) {

    // ---- Parsers ------------------------------------------------------------

    val enabledParserNames: Flow<Set<String>> = appPreferences.enabledParserNames
    val autoPushParserNames: Flow<Set<String>> = appPreferences.autoPushParserNames

    suspend fun setEnabledParserNames(names: Set<String>) = appPreferences.setEnabledParserNames(names)
    suspend fun setAutoPushParserNames(names: Set<String>) = appPreferences.setAutoPushParserNames(names)
    suspend fun enabledParsers(): List<BankParser> = appPreferences.enabledParsers()

    // ---- Daily reminder -------------------------------------------------------

    val reminderEnabled: Flow<Boolean> = appPreferences.reminderEnabled
    val reminderTimeMinutes: Flow<Int> = appPreferences.reminderTimeMinutes
    val reminderSuppressThreshold: Flow<Int> = appPreferences.reminderSuppressThreshold

    suspend fun setReminderEnabled(enabled: Boolean) = appPreferences.setReminderEnabled(enabled)
    suspend fun setReminderTimeMinutes(minutes: Int) = appPreferences.setReminderTimeMinutes(minutes)
    suspend fun setReminderSuppressThreshold(threshold: Int) =
        appPreferences.setReminderSuppressThreshold(threshold)

    // ---- SMS backfill scan state ------------------------------------------------

    val lastScannedTimestamp: Flow<Long> = appPreferences.lastScannedTimestamp
    val scanAllTime: Flow<Boolean> = appPreferences.scanAllTime

    suspend fun setLastScannedTimestamp(timestamp: Long) = appPreferences.setLastScannedTimestamp(timestamp)
    suspend fun setScanAllTime(scanAllTime: Boolean) = appPreferences.setScanAllTime(scanAllTime)

    // ---- Wallet API token -----------------------------------------------------

    /** True once an (encrypted) token is stored, without ever decrypting/exposing it. */
    val hasToken: Flow<Boolean> = secureTokenStore.hasToken

    suspend fun saveToken(token: String) = secureTokenStore.saveToken(token)
    suspend fun clearToken() = secureTokenStore.clearToken()

    // ---- Account mappings -----------------------------------------------------

    fun observeAccountMappings(): Flow<List<AccountMappingEntity>> = accountMappingDao.observeAll()
    suspend fun upsertAccountMapping(mapping: AccountMappingEntity): Long = accountMappingDao.upsert(mapping)
    suspend fun deleteAccountMapping(mapping: AccountMappingEntity) = accountMappingDao.delete(mapping)

    // ---- Category rules --------------------------------------------------------

    fun observeCategoryRules(): Flow<List<CategoryRuleEntity>> = categoryRuleDao.observeAllOrdered()
    suspend fun upsertCategoryRule(rule: CategoryRuleEntity): Long = categoryRuleDao.upsert(rule)
    suspend fun deleteCategoryRule(rule: CategoryRuleEntity) = categoryRuleDao.delete(rule)
}
