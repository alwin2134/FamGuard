package com.famguard.agent.auth

import com.famguard.contracts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import android.content.Context
import com.famguard.agent.security.DeviceKeystoreManager
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AgentApiClient(
    private val context: Context? = null,
    private var baseUrl: String = "",
    private var anonKey: String = ""
) {

    private fun ensureCredentials() {
        if ((baseUrl.isBlank() || anonKey.isBlank()) && context != null) {
            val km = DeviceKeystoreManager(context)
            val server = km.getServerUrl().trimEnd('/')
            if (server.isNotBlank()) {
                baseUrl = "$server/functions/v1"
            }
            val key = km.getAnonKey()
            if (key.isNotBlank()) {
                anonKey = key
            }
        }
    }

    fun updateCredentials(serverUrl: String, key: String) {
        if (serverUrl.isNotBlank()) this.baseUrl = "${serverUrl.trimEnd('/')}/functions/v1"
        if (key.isNotBlank()) this.anonKey = key.trim()
    }

    fun getBaseUrl(): String {
        ensureCredentials()
        return baseUrl
    }

    fun getAnonKey(): String {
        ensureCredentials()
        return anonKey
    }

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

    suspend fun completePairing(request: CompletePairingRequest): Result<CompletePairingResponse> =
        post("complete-pairing", json.encodeToString(request))

    suspend fun uploadLocation(request: UploadLocationRequest): Result<UploadLocationResponse> =
        post("update-location", json.encodeToString(request))

    suspend fun ingestActivity(request: IngestActivityRequest): Result<IngestActivityResponse> =
        post("ingest-app-activity", json.encodeToString(request))

    suspend fun ackCommand(request: DeviceCommandAckRequest): Result<DeviceCommand> =
        post("device-ack", json.encodeToString(request))

    suspend fun fetchPendingCommands(deviceId: String, deviceSecret: String): Result<List<DeviceCommand>> {
        val payload = mapOf(
            "device_id" to deviceId,
            "device_secret" to deviceSecret
        )
        return post("get-pending-commands", json.encodeToString(payload))
    }

    suspend fun stopLocationSession(
        sessionId: String,
        deviceId: String,
        deviceSecret: String,
        reason: String?
    ): Result<LocationSession> {
        val payload = mapOf(
            "session_id" to sessionId,
            "device_id" to deviceId,
            "device_secret" to deviceSecret,
            "reason" to (reason ?: "Stopped by user")
        )
        return post("stop-location-session", json.encodeToString(payload))
    }

    suspend fun revokeDevice(
        deviceId: String,
        deviceSecret: String,
        reason: String?
    ): Result<Boolean> {
        val payload = mapOf(
            "device_id" to deviceId,
            "device_secret" to deviceSecret,
            "reason" to (reason ?: "Device unlinked by target user")
        )
        return withContext(Dispatchers.IO) {
            try {
                ensureCredentials()
                val targetUrl = "${baseUrl.trimEnd('/')}/revoke-device"
                val req = Request.Builder()
                    .url(targetUrl)
                    .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) Result.success(true)
                    else Result.failure(IOException("Server error ${response.code}: ${response.body?.string()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private val seenSignalIds = mutableSetOf<String>()

    suspend fun sendSignal(deviceId: String, deviceSecret: String, payloadJson: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureCredentials()
            val parsedPayload = org.json.JSONObject(payloadJson)
            val streamType = parsedPayload.optString("stream_type", "screen_mirror")
            val signalType = parsedPayload.optString("type", "unknown")

            val postgrestUrl = baseUrl.replace("/functions/v1", "/rest/v1") + "/webrtc_signals"
            val insertObj = org.json.JSONObject().apply {
                put("device_id", deviceId)
                put("sender_type", "agent")
                put("stream_type", streamType)
                put("signal_type", signalType)
                put("payload", parsedPayload)
            }
            val req = Request.Builder()
                .url(postgrestUrl)
                .post(insertObj.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Prefer", "return=minimal")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("Failed to send WebRTC signal (${resp.code}): ${resp.body?.string()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchIncomingSignals(deviceId: String, deviceSecret: String, streamType: String): List<String> = withContext(Dispatchers.IO) {
        try {
            ensureCredentials()
            val postgrestUrl = baseUrl.replace("/functions/v1", "/rest/v1") +
                "/webrtc_signals?device_id=eq.$deviceId&sender_type=eq.controller&stream_type=eq.$streamType&order=created_at.asc&limit=15"

            val req = Request.Builder()
                .url(postgrestUrl)
                .get()
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
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

    private suspend inline fun <reified T> post(pathOrUrl: String, jsonBody: String): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                ensureCredentials()
                val targetUrl = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                    pathOrUrl
                } else {
                    "${baseUrl.trimEnd('/')}/${pathOrUrl.trimStart('/')}"
                }
                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .post(jsonBody.toRequestBody(jsonMediaType))

                if (anonKey.isNotBlank()) {
                    requestBuilder.addHeader("apikey", anonKey)
                    requestBuilder.addHeader("Authorization", "Bearer $anonKey")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Result.success(json.decodeFromString<T>(bodyString))
                    } else {
                        Result.failure(IOException("HTTP ${response.code}: $bodyString"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

