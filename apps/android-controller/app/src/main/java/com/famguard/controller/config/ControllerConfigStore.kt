package com.famguard.controller.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.famguard.controller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ControllerConfigStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "famguard_controller_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("famguard_controller_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getServerUrl(): String {
        val saved = prefs.getString("server_url", null)
        if (!saved.isNullOrBlank()) return saved.trimEnd('/')
        val defaultUrl = BuildConfig.DEFAULT_SUPABASE_URL
        if (defaultUrl.isNotBlank()) return defaultUrl.trimEnd('/')
        return ""
    }

    fun getAnonKey(): String {
        val saved = prefs.getString("anon_key", null)
        if (!saved.isNullOrBlank()) return saved.trim()
        val defaultKey = BuildConfig.DEFAULT_SUPABASE_ANON_KEY
        if (defaultKey.isNotBlank()) return defaultKey.trim()
        return ""
    }

    fun saveConfig(serverUrl: String, anonKey: String) {
        prefs.edit()
            .putString("server_url", serverUrl.trimEnd('/'))
            .putString("anon_key", anonKey.trim())
            .apply()
    }

    fun isConfigured(): Boolean {
        return getServerUrl().isNotBlank() && getAnonKey().isNotBlank()
    }

    fun saveSession(userId: String, email: String, accessToken: String, refreshToken: String = "") {
        prefs.edit()
            .putString("session_user_id", userId)
            .putString("session_email", email)
            .putString("session_access_token", accessToken)
            .putString("session_refresh_token", refreshToken)
            .apply()
    }

    fun getSession(): StoredSession? {
        val token = prefs.getString("session_access_token", null) ?: return null
        val userId = prefs.getString("session_user_id", null) ?: return null
        val email = prefs.getString("session_email", "") ?: ""
        val refreshToken = prefs.getString("session_refresh_token", "") ?: ""
        if (token.isBlank() || userId.isBlank()) return null
        return StoredSession(userId, email, token, refreshToken)
    }

    fun clearSession() {
        prefs.edit()
            .remove("session_user_id")
            .remove("session_email")
            .remove("session_access_token")
            .remove("session_refresh_token")
            .apply()
    }

    fun hasSession(): Boolean = getSession() != null

    suspend fun testConnection(serverUrl: String, anonKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$cleanUrl/rest/v1/")
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer ${anonKey.trim()}")
                .get()
                .build()

            httpClient.newCall(req).execute().use { response ->
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
}

data class StoredSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String = ""
)
