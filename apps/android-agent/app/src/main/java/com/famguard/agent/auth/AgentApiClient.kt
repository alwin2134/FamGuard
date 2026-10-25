package com.famguard.agent.auth

import com.famguard.contracts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AgentApiClient(
    private val baseUrl: String = "https://hbhahtluthtsswqavexa.supabase.co/functions/v1",
    private val anonKey: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhiaGFodGx1dGh0c3N3cWF2ZXhhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI4NDM4MTcsImV4cCI6MjEwODQxOTgxN30.ZaCR1YjhI3lIcK7z0HAl4tRaiF3UrXnFP0USim7oakM"
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

    suspend fun completePairing(request: CompletePairingRequest): Result<CompletePairingResponse> =
        post("$baseUrl/complete-pairing", json.encodeToString(request))

    suspend fun uploadLocation(request: UploadLocationRequest): Result<UploadLocationResponse> =
        post("$baseUrl/update-location", json.encodeToString(request))

    suspend fun ingestActivity(request: IngestActivityRequest): Result<IngestActivityResponse> =
        post("$baseUrl/ingest-app-activity", json.encodeToString(request))

    suspend fun ackCommand(request: DeviceCommandAckRequest): Result<DeviceCommand> =
        post("$baseUrl/device-ack", json.encodeToString(request))

    suspend fun fetchPendingCommands(deviceId: String, deviceSecret: String): Result<List<DeviceCommand>> {
        val payload = mapOf(
            "device_id" to deviceId,
            "device_secret" to deviceSecret
        )
        return post("$baseUrl/get-pending-commands", json.encodeToString(payload))
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
        return post("$baseUrl/stop-location-session", json.encodeToString(payload))
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
                val req = Request.Builder()
                    .url("$baseUrl/revoke-device")
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

    private suspend inline fun <reified T> post(url: String, jsonBody: String): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
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
