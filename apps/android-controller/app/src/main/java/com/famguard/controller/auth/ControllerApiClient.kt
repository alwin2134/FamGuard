package com.famguard.controller.auth

import com.famguard.contracts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
    private var supabaseUrl: String = "",
    private var anonKey: String = ""
) {
    fun updateCredentials(url: String, key: String) {
        if (url.isNotBlank()) this.supabaseUrl = url.trimEnd('/')
        if (key.isNotBlank()) this.anonKey = key.trim()
    }

    fun getSupabaseUrl(): String = supabaseUrl
    fun getAnonKey(): String = anonKey
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

    suspend fun fetchFamilyDevices(familyId: String = "11111111-1111-1111-1111-111111111111"): Result<List<Device>> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: anonKey
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/devices?family_id=eq.$familyId&status=eq.active&order=enrolled_at.desc&select=*")
            .get()
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: "[]"
                if (res.isSuccessful) {
                    Result.success(json.decodeFromString<List<Device>>(body))
                } else {
                    Result.failure(IOException("Failed to fetch devices (${res.code}): $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLatestLocation(deviceId: String): Result<LocationUpdate?> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: anonKey
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/location_updates?device_id=eq.$deviceId&order=recorded_at.desc&limit=1")
            .get()
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: "[]"
                if (res.isSuccessful) {
                    val list = json.decodeFromString<List<LocationUpdate>>(body)
                    Result.success(list.firstOrNull())
                } else {
                    Result.failure(IOException("Failed to fetch location: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCommands(deviceId: String): Result<List<DeviceCommand>> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: anonKey
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/device_commands?device_id=eq.$deviceId&order=created_at.desc&limit=20")
            .get()
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: "[]"
                if (res.isSuccessful) {
                    Result.success(json.decodeFromString<List<DeviceCommand>>(body))
                } else {
                    Result.failure(IOException("Failed to fetch commands: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchCommand(
        deviceId: String,
        action: CommandAction,
        payloadMap: Map<String, String> = emptyMap()
    ): Result<DeviceCommand> {
        val actionStr = when (action) {
            CommandAction.RING -> "ring"
            CommandAction.REFRESH_LOCATION -> "refresh_location"
            CommandAction.LOCK_APP -> "lock_app"
            CommandAction.UNLOCK_APP -> "unlock_app"
            CommandAction.SYNC_ACTIVITY -> "sync_activity"
            CommandAction.STOP_LOCATION -> "stop_location"
            CommandAction.START_SCREEN_MIRROR -> "start_screen_mirror"
            CommandAction.STOP_SCREEN_MIRROR -> "stop_screen_mirror"
            CommandAction.START_VOICE_CALL -> "start_voice_call"
            CommandAction.END_VOICE_CALL -> "end_voice_call"
            CommandAction.WEBRTC_SIGNAL -> "webrtc_signal"
        }
        val jsonObject = buildJsonObject {
            put("device_id", deviceId)
            put("action", actionStr)
            if (payloadMap.isNotEmpty()) {
                putJsonObject("payload") {
                    for ((k, v) in payloadMap) {
                        put(k, v)
                    }
                }
            }
        }
        return postWithAuth("$supabaseUrl/functions/v1/device-command", jsonObject.toString())
    }

    private val seenSignalIds = mutableSetOf<String>()

    suspend fun sendSignal(deviceId: String, payloadJson: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: anonKey
        try {
            val parsedPayload = org.json.JSONObject(payloadJson)
            val streamType = parsedPayload.optString("stream_type", "screen_mirror")
            val signalType = parsedPayload.optString("type", "unknown")

            val postgrestUrl = "$supabaseUrl/rest/v1/webrtc_signals"
            val insertObj = org.json.JSONObject().apply {
                put("device_id", deviceId)
                put("sender_type", "controller")
                put("stream_type", streamType)
                put("signal_type", signalType)
                put("payload", parsedPayload)
            }
            val req = Request.Builder()
                .url(postgrestUrl)
                .post(insertObj.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "return=minimal")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(IOException("Failed to send controller WebRTC signal: ${resp.body?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchIncomingSignals(deviceId: String, streamType: String): List<String> = withContext(Dispatchers.IO) {
        val token = currentUser?.accessToken ?: anonKey
        try {
            val postgrestUrl = "$supabaseUrl/rest/v1/webrtc_signals?device_id=eq.$deviceId&sender_type=eq.agent&stream_type=eq.$streamType&order=created_at.asc&limit=15"
            val req = Request.Builder()
                .url(postgrestUrl)
                .get()
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) return@withContext emptyList()
                val array = org.json.JSONArray(body)
                val newSignals = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optString("id")
                    if (id.isNotBlank() && !seenSignalIds.contains(id)) {
                        seenSignalIds.add(id)
                        val payload = item.optJSONObject("payload")
                        if (payload != null) {
                            newSignals.add(payload.toString())
                        } else {
                            newSignals.add(item.toString())
                        }
                    }
                }
                newSignals
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

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
