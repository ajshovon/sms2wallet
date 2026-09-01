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
    val hasToken: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[TOKEN_KEY] != null }

    /** Encrypts [token] with a fresh random IV and persists `Base64(iv + ciphertext)`. */
    suspend fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        dataStore.edit { prefs -> prefs[TOKEN_KEY] = payload }
    }

    /**
     * Decrypts and returns the stored token, or null if none is stored.
     *
     * If the Keystore key was permanently invalidated (e.g. the device's lock-screen
     * credential was removed/changed while a key required authentication, or the key was
     * otherwise revoked by the OS) or decryption fails for any other reason, the stale blob
     * is cleared and null is returned rather than throwing - the caller simply re-prompts the
     * user to re-enter their token.
     */
    suspend fun getToken(): String? {
        val encoded = dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[TOKEN_KEY] ?: return null

        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return clearAndReturnNull()
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearAndReturnNull()
        } catch (e: AEADBadTagException) {
            clearAndReturnNull()
        } catch (e: BadPaddingException) {
            clearAndReturnNull()
        } catch (e: IllegalBlockSizeException) {
            clearAndReturnNull()
        } catch (e: IllegalArgumentException) {
            // Malformed Base64 payload.
            clearAndReturnNull()
        }
    }

    suspend fun clearToken() {
        dataStore.edit { prefs -> prefs.remove(TOKEN_KEY) }
    }

    private suspend fun clearAndReturnNull(): String? {
        clearToken()
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
    }
}
