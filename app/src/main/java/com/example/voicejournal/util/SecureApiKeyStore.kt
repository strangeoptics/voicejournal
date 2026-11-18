package com.example.voicejournal.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores and retrieves the Google Cloud API key encrypted via Android Keystore (AES-256-GCM).
 * Automatically migrates any legacy plaintext key from default SharedPreferences on first access.
 */
object SecureApiKeyStore {

    const val SECURE_PREFS_NAME = "voice_journal_secure_prefs"
    private const val KEY_ENCRYPTED_API_KEY = "encrypted_google_cloud_api_key"
    private const val LEGACY_PLAINTEXT_KEY = "google_cloud_api_key"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "voicejournal_google_cloud_api_key_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PAYLOAD_SEPARATOR = ":"

    private val lock = Any()
    private var initialized = false
    private val _apiKeyFlow = MutableStateFlow("")
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    fun getApiKey(context: Context, legacyPrefs: SharedPreferences? = null): String {
        ensureInitialized(context.applicationContext, legacyPrefs)
        return _apiKeyFlow.value
    }

    fun saveApiKey(context: Context, apiKey: String, legacyPrefs: SharedPreferences? = null) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val securePrefs = appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            // Always ensure no plaintext copy remains in legacy preferences
            legacyPrefs?.let { prefs ->
                if (prefs.contains(LEGACY_PLAINTEXT_KEY)) {
                    prefs.edit { remove(LEGACY_PLAINTEXT_KEY) }
                }
            }

            if (apiKey.isBlank()) {
                securePrefs.edit { remove(KEY_ENCRYPTED_API_KEY) }
                _apiKeyFlow.value = ""
                initialized = true
                return
            }

            try {
                val encryptedPayload = encrypt(apiKey)
                securePrefs.edit { putString(KEY_ENCRYPTED_API_KEY, encryptedPayload) }
                _apiKeyFlow.value = apiKey
                initialized = true
            } catch (e: Exception) {
                Log.e("SecureApiKeyStore", "Failed to encrypt API key", e)
            }
        }
    }

    private fun ensureInitialized(appContext: Context, legacyPrefs: SharedPreferences?) {
        synchronized(lock) {
            val prefsToMigrate = legacyPrefs
                ?: appContext.getSharedPreferences("voice_journal_prefs", Context.MODE_PRIVATE)
            val securePrefs = appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

            // Migrate legacy plaintext key if present
            if (prefsToMigrate.contains(LEGACY_PLAINTEXT_KEY)) {
                val legacyPlaintext = prefsToMigrate.getString(LEGACY_PLAINTEXT_KEY, null)
                prefsToMigrate.edit { remove(LEGACY_PLAINTEXT_KEY) }
                if (!legacyPlaintext.isNullOrBlank()) {
                    try {
                        val encryptedPayload = encrypt(legacyPlaintext)
                        securePrefs.edit { putString(KEY_ENCRYPTED_API_KEY, encryptedPayload) }
                        _apiKeyFlow.value = legacyPlaintext
                        initialized = true
                        return
                    } catch (e: Exception) {
                        Log.e("SecureApiKeyStore", "Failed to migrate legacy API key", e)
                    }
                }
            }

            if (initialized) {
                return
            }

            val storedPayload = securePrefs.getString(KEY_ENCRYPTED_API_KEY, null)
            _apiKeyFlow.value = if (!storedPayload.isNullOrBlank()) {
                try {
                    decrypt(storedPayload)
                } catch (e: Exception) {
                    Log.w("SecureApiKeyStore", "Failed to decrypt stored API key; clearing invalid entry", e)
                    securePrefs.edit { remove(KEY_ENCRYPTED_API_KEY) }
                    ""
                }
            } else {
                ""
            }
            initialized = true
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivBase64$PAYLOAD_SEPARATOR$cipherBase64"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(PAYLOAD_SEPARATOR, limit = 2)
        require(parts.size == 2) { "Invalid encrypted payload format" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
