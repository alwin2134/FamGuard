package com.famguard.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class MemberRole {
    @SerialName("guardian")
    GUARDIAN,
    @SerialName("ward")
    WARD
}

@Serializable
enum class DeviceStatus {
    @SerialName("active")
    ACTIVE,
    @SerialName("revoked")
    REVOKED,
    @SerialName("pending_pairing")
    PENDING_PAIRING
}

@Serializable
enum class LocationSessionStatus {
    @SerialName("requested")
    REQUESTED,
    @SerialName("authorized")
    AUTHORIZED,
    @SerialName("starting")
    STARTING,
    @SerialName("active")
    ACTIVE,
    @SerialName("stop_requested")
    STOP_REQUESTED,
    @SerialName("stopped")
    STOPPED,
    @SerialName("expired")
    EXPIRED,
    @SerialName("blocked")
    BLOCKED,
    @SerialName("failed")
    FAILED
}

@Serializable
enum class AppEventType {
    @SerialName("foreground")
    FOREGROUND,
    @SerialName("background")
    BACKGROUND,
    @SerialName("usage_summary")
    USAGE_SUMMARY
}

@Serializable
enum class CommandAction {
    @SerialName("refresh_location")
    REFRESH_LOCATION,
    @SerialName("ring")
    RING,
    @SerialName("lock_app")
    LOCK_APP,
    @SerialName("unlock_app")
    UNLOCK_APP,
    @SerialName("sync_activity")
    SYNC_ACTIVITY,
    @SerialName("stop_location")
    STOP_LOCATION,
    @SerialName("start_screen_mirror")
    START_SCREEN_MIRROR,
    @SerialName("stop_screen_mirror")
    STOP_SCREEN_MIRROR,
    @SerialName("start_voice_call")
    START_VOICE_CALL,
    @SerialName("end_voice_call")
    END_VOICE_CALL,
    @SerialName("webrtc_signal")
    WEBRTC_SIGNAL
}

@Serializable
enum class CommandStatus {
    @SerialName("pending")
    PENDING,
    @SerialName("delivered")
    DELIVERED,
    @SerialName("executing")
    EXECUTING,
    @SerialName("succeeded")
    SUCCEEDED,
    @SerialName("failed")
    FAILED,
    @SerialName("expired")
    EXPIRED,
    @SerialName("cancelled")
    CANCELLED,
    @SerialName("blocked")
    BLOCKED
}

@Serializable
data class Family(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class FamilyMember(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    val role: MemberRole,
    @SerialName("display_name") val displayName: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class Device(
    val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val model: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    val status: DeviceStatus = DeviceStatus.ACTIVE,
    @SerialName("enrolled_at") val enrolledAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    @SerialName("is_online") val isOnline: Boolean = false
)

@Serializable
data class DeviceConsent(
    @SerialName("device_id") val deviceId: String,
    @SerialName("location_sharing_enabled") val locationSharingEnabled: Boolean = false,
    @SerialName("app_activity_enabled") val appActivityEnabled: Boolean = false,
    @SerialName("remote_commands_enabled") val remoteCommandsEnabled: Boolean = false,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class LocationSession(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("requested_by") val requestedBy: String,
    val status: LocationSessionStatus,
    @SerialName("created_at") val createdAt: String,
    @SerialName("authorized_at") val authorizedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("stopped_at") val stoppedAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null
)

@Serializable
data class LocationUpdate(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("session_id") val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Float,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("received_at") val receivedAt: String? = null
)

@Serializable
data class DeviceAppEvent(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_label") val appLabel: String? = null,
    @SerialName("event_type") val eventType: AppEventType,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
    val source: String = "android_usage_events"
)

@Serializable
data class DeviceCommand(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("requested_by") val requestedBy: String,
    val action: CommandAction,
    @SerialName("payload_json") val payloadJson: JsonElement? = null,
    val status: CommandStatus,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("executed_at") val executedAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null
)

// Pairing API DTOs
@Serializable
data class CreatePairingRequest(
    @SerialName("family_id") val familyId: String,
    @SerialName("target_name") val targetName: String
)

@Serializable
data class CreatePairingResponse(
    @SerialName("pairing_token") val pairingToken: String,
    @SerialName("qr_payload") val qrPayload: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class QrPayload(
    @SerialName("token") val token: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("family_name") val familyName: String,
    @SerialName("guardian_name") val guardianName: String,
    @SerialName("server_url") val serverUrl: String,
    @SerialName("anon_key") val anonKey: String? = null
)

@Serializable
data class CompletePairingRequest(
    @SerialName("token") val token: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("os_version") val osVersion: String,
    @SerialName("device_public_key") val devicePublicKey: String? = null,
    @SerialName("consents") val consents: DeviceConsentInput
)

@Serializable
data class DeviceConsentInput(
    @SerialName("location_sharing") val locationSharing: Boolean,
    @SerialName("app_activity") val appActivity: Boolean,
    @SerialName("remote_commands") val remoteCommands: Boolean
)

@Serializable
data class CompletePairingResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_secret") val deviceSecret: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("family_name") val familyName: String
)

// Ingest App Activity DTO
@Serializable
data class InstalledAppDto(
    @SerialName("package_name") val packageName: String,
    @SerialName("app_label") val appLabel: String,
    @SerialName("is_system") val isSystem: Boolean = false
)

@Serializable
data class IngestActivityRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_secret") val deviceSecret: String,
    val events: List<DeviceAppEvent>,
    @SerialName("installed_apps") val installedApps: List<InstalledAppDto>? = null
)

@Serializable
data class IngestActivityResponse(
    val ingested: Int,
    @SerialName("skipped_duplicates") val skippedDuplicates: Int
)

// Location upload DTO
@Serializable
data class UploadLocationRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_secret") val deviceSecret: String,
    @SerialName("session_id") val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Float,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class UploadLocationResponse(
    val success: Boolean,
    @SerialName("session_status") val sessionStatus: LocationSessionStatus
)

// Device command execution ACK DTO
@Serializable
data class DeviceCommandAckRequest(
    @SerialName("command_id") val commandId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_secret") val deviceSecret: String,
    val status: CommandStatus,
    @SerialName("failure_reason") val failureReason: String? = null
)
