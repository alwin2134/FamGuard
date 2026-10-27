package com.famguard.controller.auth

import com.famguard.contracts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthUser(
    val id: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String = ""
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
    var onSessionPersist: ((AuthUser) -> Unit)? = null

    suspend fun signIn(email: String, pass: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Server URL and API Key are not configured. Tap 'Configure Server & Updates' below to set them.")
            )
        }
        try {
            val payload = mapOf("email" to email, "password" to pass)
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
                .addHeader("apikey", anonKey)
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString<JsonObject>(body)
                    val token = parsed["access_token"]?.jsonPrimitive?.contentOrNull
                        ?: parsed["access_token"]?.toString()?.replace("\"", "") ?: ""
                    val refreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull
                        ?: parsed["refresh_token"]?.toString()?.replace("\"", "") ?: ""
                    val userObj = parsed["user"] as? JsonObject
                    val userId = userObj?.get("id")?.jsonPrimitive?.contentOrNull
                        ?: userObj?.get("id")?.toString()?.replace("\"", "") ?: ""
                    val authUser = AuthUser(userId, email, token, refreshToken)
                    currentUser = authUser
                    onSessionPersist?.invoke(authUser)
                    Result.success(authUser)
                } else {
                    Result.failure(IOException("Sign in failed (${response.code}): $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshSession(refreshToken: String = currentUser?.refreshToken ?: ""): Result<AuthUser> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || anonKey.isBlank() || refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Cannot refresh session: credentials or refresh token missing"))
        }
        try {
            val payload = mapOf("refresh_token" to refreshToken)
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
                .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
                .addHeader("apikey", anonKey)
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString<JsonObject>(body)
                    val token = parsed["access_token"]?.jsonPrimitive?.contentOrNull
                        ?: parsed["access_token"]?.toString()?.replace("\"", "") ?: ""
                    val newRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull
                        ?: parsed["refresh_token"]?.toString()?.replace("\"", "") ?: refreshToken
                    val userObj = parsed["user"] as? JsonObject
                    val userId = userObj?.get("id")?.jsonPrimitive?.contentOrNull
                        ?: userObj?.get("id")?.toString()?.replace("\"", "") ?: currentUser?.id ?: ""
                    val email = userObj?.get("email")?.jsonPrimitive?.contentOrNull
                        ?: userObj?.get("email")?.toString()?.replace("\"", "") ?: currentUser?.email ?: ""
                    val authUser = AuthUser(userId, email, token, newRefreshToken)
                    currentUser = authUser
                    onSessionPersist?.invoke(authUser)
                    Result.success(authUser)
                } else {
                    Result.failure(IOException("Session refresh failed (${response.code}): $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    var currentFamilyId: String = "11111111-1111-1111-1111-111111111111"

    suspend fun resolveFamilyId(): String = withContext(Dispatchers.IO) {
        if (currentFamilyId != "11111111-1111-1111-1111-111111111111" && currentFamilyId.isNotBlank()) {
            return@withContext currentFamilyId
        }
        val userId = currentUser?.id ?: return@withContext currentFamilyId
        val token = currentUser?.accessToken ?: anonKey
        try {
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/family_members?user_id=eq.$userId&role=eq.guardian&select=family_id&limit=1")
                .get()
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: "[]"
                if (res.isSuccessful) {
                    val array = json.parseToJsonElement(body).jsonArray
                    if (array.isNotEmpty()) {
                        val fid = array[0].jsonObject["family_id"]?.jsonPrimitive?.content ?: ""
                        if (fid.isNotBlank()) {
                            currentFamilyId = fid
                            return@withContext fid
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        currentFamilyId
    }

    suspend fun createPairing(familyId: String = "11111111-1111-1111-1111-111111111111", targetName: String): Result<CreatePairingResponse> {
        val targetFid = resolveFamilyId()
        val res = postWithAuth<CreatePairingResponse>(
            "$supabaseUrl/functions/v1/create-pairing",
            json.encodeToString(CreatePairingRequest(targetFid, targetName))
        )
        res.onSuccess { pairing ->
            try {
                val parsed = json.decodeFromString<QrPayload>(pairing.qrPayload)
                if (parsed.familyId.isNotBlank()) {
                    currentFamilyId = parsed.familyId
                }
            } catch (_: Exception) {}
        }
        return res
    }

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

    suspend fun fetchFamilyDevices(familyId: String? = null, isRetry: Boolean = false): Result<List<Device>> = withContext(Dispatchers.IO) {
        val targetFamilyId = if (!familyId.isNullOrBlank() && familyId != "11111111-1111-1111-1111-111111111111") {
            familyId
        } else {
            resolveFamilyId()
        }
        val token = currentUser?.accessToken ?: anonKey
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/devices?family_id=eq.$targetFamilyId&status=eq.active&order=enrolled_at.desc&select=*")
            .get()
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: "[]"
                if (res.isSuccessful) {
                    Result.success(json.decodeFromString<List<Device>>(body))
                } else if (res.code == 401 && !isRetry && !currentUser?.refreshToken.isNullOrBlank()) {
                    val refreshRes = refreshSession(currentUser!!.refreshToken)
                    if (refreshRes.isSuccess) {
                        return@withContext fetchFamilyDevices(familyId, isRetry = true)
                    } else {
                        Result.failure(IOException("Failed to fetch devices (401): Session expired"))
                    }
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
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Server URL not configured"))
        }
        var attempts = 0
        while (attempts < 2) {
            attempts++
            val token = currentUser?.accessToken ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val (isSuccess, code, body) = client.newCall(req).execute().use { res ->
                    Triple(res.isSuccessful, res.code, res.body?.string() ?: "")
                }

                if (isSuccess) {
                    return@withContext Result.success(json.decodeFromString<T>(body))
                } else if (code == 401 && attempts == 1 && !currentUser?.refreshToken.isNullOrBlank()) {
                    val refreshRes = refreshSession(currentUser!!.refreshToken)
                    if (!refreshRes.isSuccess) {
                        return@withContext Result.failure(IOException("HTTP 401: Token expired and refresh failed"))
                    }
                    // Loop continues and retries with refreshed token
                } else {
                    return@withContext Result.failure(IOException("HTTP $code: $body"))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(IOException("Request failed after refresh attempt"))
    }
}
