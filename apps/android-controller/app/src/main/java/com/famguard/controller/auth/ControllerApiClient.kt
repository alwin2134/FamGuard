package com.famguard.controller.auth

import com.famguard.contracts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthUser(
    val id: String,
    val email: String,
    val accessToken: String
)

class ControllerApiClient(
    private val supabaseUrl: String = "https://your-project.supabase.co",
    private val anonKey: String = ""
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var currentUser: AuthUser? = null

    suspend fun signIn(email: String, pass: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val payload = mapOf("email" to email, "password" to pass)
        val req = Request.Builder()
            .url("$supabaseUrl/auth/v1/token?grant_type=password")
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .addHeader("apikey", anonKey)
            .build()

        try {
            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString<JsonObject>(body)
                    val token = parsed["access_token"]?.toString()?.replace("\"", "") ?: ""
                    val userObj = parsed["user"] as? JsonObject
                    val userId = userObj?.get("id")?.toString()?.replace("\"", "") ?: ""
                    val authUser = AuthUser(userId, email, token)
                    currentUser = authUser
                    Result.success(authUser)
                } else {
                    Result.failure(IOException("Sign in failed (${response.code}): $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPairing(familyId: String, targetName: String): Result<CreatePairingResponse> =
        postWithAuth("$supabaseUrl/functions/v1/create-pairing", json.encodeToString(CreatePairingRequest(familyId, targetName)))

    suspend fun startLocationSession(deviceId: String, durationMinutes: Int = 5): Result<LocationSession> =
        postWithAuth(
            "$supabaseUrl/functions/v1/start-location-session",
            json.encodeToString(mapOf("device_id" to deviceId, "duration_minutes" to durationMinutes.toString()))
        )

    suspend fun stopLocationSession(sessionId: String): Result<LocationSession> =
        postWithAuth(
            "$supabaseUrl/functions/v1/stop-location-session",
            json.encodeToString(mapOf("session_id" to sessionId))
        )

    suspend fun getAppActivity(deviceId: String, limit: Int = 50): Result<String> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val req = Request.Builder()
            .url("$supabaseUrl/functions/v1/get-app-activity?device_id=$deviceId&limit=$limit")
            .get()
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (res.isSuccessful) Result.success(body)
                else Result.failure(IOException("Failed to get activity: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchCommand(deviceId: String, action: CommandAction, payload: Map<String, String> = emptyMap()): Result<DeviceCommand> =
        postWithAuth(
            "$supabaseUrl/functions/v1/device-command",
            json.encodeToString(mapOf("device_id" to deviceId, "action" to action.name.lowercase(), "payload" to json.encodeToString(payload)))
        )

    private suspend inline fun <reified T> postWithAuth(url: String, jsonBody: String): Result<T> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val req = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    Result.success(json.decodeFromString<T>(body))
                } else {
                    Result.failure(IOException("HTTP ${res.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
