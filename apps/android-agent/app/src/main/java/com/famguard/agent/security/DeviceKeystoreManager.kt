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

    fun updateLastLocationFix(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_LOCATION_FIX, timestampMs).apply()
    }

    fun getLastLocationFix(): Long = prefs.getLong(KEY_LAST_LOCATION_FIX, 0L)

    fun updateLastSyncTimestamp(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestampMs).apply()
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    companion object {
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
