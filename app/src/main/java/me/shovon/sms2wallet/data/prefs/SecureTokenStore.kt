package me.shovon.sms2wallet.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.util.Base64

/** Dedicated DataStore file, kept separate from [AppPreferences] so the (encrypted) token
 * blob never shares a preferences file - and therefore never shares a backup/restore or
 * debugging surface - with ordinary app settings. */
private val Context.secureTokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_token_store"
)

/**
 * Encrypts and persists the BudgetBakers Wallet API token.
 *
 * Jetpack Security's `EncryptedSharedPreferences` is deprecated, so this wraps the Wallet
 * token in AES-256/GCM using a non-exportable key held in the Android Keystore, and stores
 * only `Base64(iv + ciphertext)` in DataStore. The key never leaves the secure hardware (or
 * software Keystore fallback) - DataStore only ever sees opaque bytes.
 *
 * The token itself is never logged; callers must not log [getToken]'s result either.
 */
class SecureTokenStore(
    @ApplicationContext private val context: Context
) {

    private val dataStore get() = context.secureTokenDataStore

    /** True once an (encrypted) token blob is present, without ever decrypting it. */
    val hasToken: Flow<Boolean> = presenceOf(TOKEN_KEY)

    /**
     * True once a Gemini API key is stored. Kept in the same encrypted store as the Wallet
     * token: it is the same class of secret - a bearer credential that bills the user's account
     * if it leaks - and giving it a weaker home would be the only thing that made it weaker.
     */
    val hasGeminiApiKey: Flow<Boolean> = presenceOf(GEMINI_API_KEY_KEY)

    /** Encrypts [token] with a fresh random IV and persists `Base64(iv + ciphertext)`. */
    suspend fun saveToken(token: String) = putEncrypted(TOKEN_KEY, token)

    /**
     * Decrypts and returns the stored token, or null if none is stored.
     *
     * If the Keystore key was permanently invalidated (e.g. the device's lock-screen
     * credential was removed/changed while a key required authentication, or the key was
     * otherwise revoked by the OS) or decryption fails for any other reason, the stale blob
     * is cleared and null is returned rather than throwing - the caller simply re-prompts the
     * user to re-enter their token.
     */
    suspend fun getToken(): String? = readEncrypted(TOKEN_KEY)

    suspend fun clearToken() {
        dataStore.edit { prefs -> prefs.remove(TOKEN_KEY) }
    }

    suspend fun saveGeminiApiKey(apiKey: String) = putEncrypted(GEMINI_API_KEY_KEY, apiKey)

    /** Decrypts and returns the stored Gemini API key, or null. Must never be logged. */
    suspend fun getGeminiApiKey(): String? = readEncrypted(GEMINI_API_KEY_KEY)

    suspend fun clearGeminiApiKey() {
        dataStore.edit { prefs -> prefs.remove(GEMINI_API_KEY_KEY) }
    }

    // ---- Shared crypto -------------------------------------------------------------

    private fun presenceOf(key: Preferences.Key<String>): Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[key] != null }

    private suspend fun putEncrypted(key: Preferences.Key<String>, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        dataStore.edit { prefs -> prefs[key] = payload }
    }

    /**
     * Mirrors [getToken]'s failure handling: an undecryptable blob is cleared and reported as
     * absent, so the user is re-prompted instead of being stuck behind a permanent error.
     */
    private suspend fun readEncrypted(key: Preferences.Key<String>): String? {
        val encoded = dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[key] ?: return null

        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return clearAndReturnNull(key)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearAndReturnNull(key)
        } catch (e: AEADBadTagException) {
            clearAndReturnNull(key)
        } catch (e: BadPaddingException) {
            clearAndReturnNull(key)
        } catch (e: IllegalBlockSizeException) {
            clearAndReturnNull(key)
        } catch (e: IllegalArgumentException) {
            clearAndReturnNull(key)
        }
    }

    private suspend fun clearAndReturnNull(key: Preferences.Key<String>): String? {
        dataStore.edit { prefs -> prefs.remove(key) }
        return null
    }

    /** Loads the AndroidKeyStore AES key for [KEY_ALIAS], generating it on first use. */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sms2wallet_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_IV_LENGTH_BYTES = 12
        val TOKEN_KEY = stringPreferencesKey("encrypted_wallet_token")
        val GEMINI_API_KEY_KEY = stringPreferencesKey("encrypted_gemini_api_key")
    }
}
