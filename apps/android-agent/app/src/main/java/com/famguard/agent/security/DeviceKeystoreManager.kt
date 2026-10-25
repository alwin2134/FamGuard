package com.famguard.agent.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DeviceKeystoreManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "famguard_agent_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveDeviceCredentials(
        deviceId: String,
        deviceSecret: String,
        familyId: String,
        familyName: String
    ) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_SECRET, deviceSecret)
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_FAMILY_NAME, familyName)
            .putBoolean(KEY_IS_PAIRED, true)
            .apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun getDeviceSecret(): String? = prefs.getString(KEY_DEVICE_SECRET, null)
    fun getFamilyId(): String? = prefs.getString(KEY_FAMILY_ID, null)
    fun getFamilyName(): String? = prefs.getString(KEY_FAMILY_NAME, null)
    fun isPaired(): Boolean = prefs.getBoolean(KEY_IS_PAIRED, false) && getDeviceId() != null

    fun saveActiveSession(sessionId: String?, expiresAtMs: Long?) {
        val editor = prefs.edit()
        if (sessionId == null) {
            editor.remove(KEY_ACTIVE_SESSION_ID)
            editor.remove(KEY_ACTIVE_SESSION_EXPIRES)
        } else {
            editor.putString(KEY_ACTIVE_SESSION_ID, sessionId)
            editor.putLong(KEY_ACTIVE_SESSION_EXPIRES, expiresAtMs ?: 0L)
        }
        editor.apply()
    }

    fun getActiveSessionId(): String? {
        val expiresAt = prefs.getLong(KEY_ACTIVE_SESSION_EXPIRES, 0L)
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            saveActiveSession(null, null)
            return null
        }
        return prefs.getString(KEY_ACTIVE_SESSION_ID, null)
    }

    fun getActiveSessionExpiresAt(): Long = prefs.getLong(KEY_ACTIVE_SESSION_EXPIRES, 0L)

    fun saveServerConfig(serverUrl: String, anonKey: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl.trimEnd('/'))
            .putString(KEY_ANON_KEY, anonKey.trim())
            .apply()
    }

    fun getServerUrl(): String {
        val saved = prefs.getString(KEY_SERVER_URL, null)
        if (!saved.isNullOrBlank()) return saved.trimEnd('/')
        val defaultUrl = com.famguard.agent.BuildConfig.DEFAULT_SUPABASE_URL
        if (defaultUrl.isNotBlank()) return defaultUrl.trimEnd('/')
        return "https://hbhahtluthtsswqavexa.supabase.co"
    }

    fun getAnonKey(): String {
        val saved = prefs.getString(KEY_ANON_KEY, null)
        if (!saved.isNullOrBlank()) return saved.trim()
        val defaultKey = com.famguard.agent.BuildConfig.DEFAULT_SUPABASE_ANON_KEY
        if (defaultKey.isNotBlank()) return defaultKey.trim()
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhiaGFodGx1dGh0c3N3cWF2ZXhhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI4NDM4MTcsImV4cCI6MjEwODQxOTgxN30.ZaCR1YjhI3lIcK7z0HAl4tRaiF3UrXnFP0USim7oakM"
    }

    suspend fun testConnection(serverUrl: String, anonKey: String): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trimEnd('/')
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder()
                .url("$cleanUrl/rest/v1/")
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer ${anonKey.trim()}")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful || response.code == 200 || response.code == 401 || response.code == 404) {
                    Result.success("Connected successfully! Server reachable (HTTP ${response.code}).")
                } else {
                    Result.failure(Exception("Server returned HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    fun updateLastLocationFix(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_LOCATION_FIX, timestampMs).apply()
    }

    fun getLastLocationFix(): Long = prefs.getLong(KEY_LAST_LOCATION_FIX, 0L)

    fun updateLastSyncTimestamp(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestampMs).apply()
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

    fun clearCredentials() {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val anonKey = prefs.getString(KEY_ANON_KEY, null)
        prefs.edit().clear().apply()
        if (!serverUrl.isNullOrBlank() || !anonKey.isNullOrBlank()) {
            saveServerConfig(serverUrl ?: "", anonKey ?: "")
        }
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ANON_KEY = "anon_key"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_FAMILY_ID = "family_id"
        private const val KEY_FAMILY_NAME = "family_name"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_ACTIVE_SESSION_EXPIRES = "active_session_expires"
        private const val KEY_LAST_LOCATION_FIX = "last_location_fix"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }
}
