package com.sappho.audiobooks.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = createEncryptedPrefs()

    private val _isAuthenticated = MutableStateFlow(getTokenSync() != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    // Triggered when server returns 401 Unauthorized
    private val _authError = MutableStateFlow(false)
    val authError: StateFlow<Boolean> = _authError

    fun triggerAuthError() {
        Log.d("AuthRepository", "Auth error triggered - token expired or invalid")
        _authError.value = true
    }

    fun clearAuthError() {
        _authError.value = false
    }

    fun saveToken(token: String) {
        securePrefs.edit().putString(KEY_TOKEN, token).apply()
        _isAuthenticated.value = true
    }

    fun getTokenSync(): String? {
        return securePrefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        securePrefs.edit().remove(KEY_TOKEN).apply()
        _isAuthenticated.value = false
    }

    fun saveServerUrl(url: String) {
        securePrefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrlSync(): String? {
        return securePrefs.getString(KEY_SERVER_URL, null)
    }

    fun hasServerUrl(): Boolean {
        return getServerUrlSync() != null
    }

    // Cache user info for offline display
    fun saveUserInfo(username: String, displayName: String?) {
        securePrefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    fun getCachedUsername(): String? {
        return securePrefs.getString(KEY_USERNAME, null)
    }

    fun getCachedDisplayName(): String? {
        return securePrefs.getString(KEY_DISPLAY_NAME, null)
    }

    fun clearUserInfo() {
        securePrefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to decrypt preferences, clearing corrupted data", e)
            clearCorruptedData()
            // Create new master key and prefs after clearing
            val newMasterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                newMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun clearCorruptedData() {
        // Clear the SharedPreferences file
        try {
            val prefsFile = File(context.filesDir.parent, "shared_prefs/$PREFS_NAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
                Log.d("AuthRepository", "Deleted corrupted prefs file")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to delete corrupted prefs file", e)
        }

        // Clear the master key from Android Keystore
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            Log.d("AuthRepository", "Deleted corrupted master key from Keystore")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to delete master key from Keystore", e)
        }

        // Also clear the Tink keyset preferences
        try {
            val tinkPrefsFile = File(context.filesDir.parent, "shared_prefs/__androidx_security_crypto_encrypted_prefs__.xml")
            if (tinkPrefsFile.exists()) {
                tinkPrefsFile.delete()
                Log.d("AuthRepository", "Deleted Tink keyset file")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to delete Tink keyset file", e)
        }
    }

    companion object {
        private const val PREFS_NAME = "secure_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "cached_username"
        private const val KEY_DISPLAY_NAME = "cached_display_name"
    }
}
