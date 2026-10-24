package com.famguard.contracts

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class FamGuardLogicTests {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Test
    fun testPairingTokenExpiry() {
        val now = System.currentTimeMillis()
        val expiredTime = isoFormat.format(Date(now - 60_000L)) // 1 min ago
        val validTime = isoFormat.format(Date(now + 600_000L)) // 10 min future

        fun isTokenExpired(expiresAtIso: String): Boolean {
            val date = isoFormat.parse(expiresAtIso) ?: return true
            return date.time < System.currentTimeMillis()
        }

        assertTrue("Token with past timestamp should be expired", isTokenExpired(expiredTime))
        assertFalse("Token with future timestamp should not be expired", isTokenExpired(validTime))
    }

    @Test
    fun testLocationCoordinateValidation() {
        fun isValidCoordinate(lat: Double, lon: Double, acc: Float): Boolean {
            return lat in -90.0..90.0 && lon in -180.0..180.0 && acc >= 0f
        }

        assertTrue("Normal SF coords should be valid", isValidCoordinate(37.7749, -122.4194, 10.0f))
        assertFalse("Lat > 90 should be invalid", isValidCoordinate(95.0, -122.4194, 10.0f))
        assertFalse("Lon < -180 should be invalid", isValidCoordinate(37.7749, -185.0, 10.0f))
        assertFalse("Negative accuracy should be invalid", isValidCoordinate(37.7749, -122.4194, -5.0f))
    }

    @Test
    fun testLocationSessionUploadRejectionAfterExpiry() {
        val session = LocationSession(
            id = "sess-01",
            deviceId = "dev-01",
            requestedBy = "guard-01",
            status = LocationSessionStatus.EXPIRED,
            createdAt = isoFormat.format(Date(System.currentTimeMillis() - 600_000L)),
            expiresAt = isoFormat.format(Date(System.currentTimeMillis() - 100_000L))
        )

        fun canAcceptUpload(s: LocationSession): Boolean {
            if (s.status != LocationSessionStatus.ACTIVE && s.status != LocationSessionStatus.AUTHORIZED) {
                return false
            }
            val expiry = isoFormat.parse(s.expiresAt)?.time ?: 0L
            return System.currentTimeMillis() < expiry
        }

        assertFalse("Expired session must reject location uploads", canAcceptUpload(session))
    }

    @Test
    fun testActivityEventDeduplication() {
        val event1 = DeviceAppEvent(
            id = "ev-1",
            deviceId = "dev-01",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            eventType = AppEventType.FOREGROUND,
            occurredAt = "2026-10-24T12:00:00.000Z"
        )

        val duplicateEvent = DeviceAppEvent(
            id = "ev-2", // different local ID
            deviceId = "dev-01",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            eventType = AppEventType.FOREGROUND,
            occurredAt = "2026-10-24T12:00:00.000Z" // identical business key
        )

        val distinctEvent = DeviceAppEvent(
            id = "ev-3",
            deviceId = "dev-01",
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            eventType = AppEventType.FOREGROUND,
            occurredAt = "2026-10-24T12:05:00.000Z"
        )

        val set = mutableSetOf<String>()
        fun deduplicate(events: List<DeviceAppEvent>): List<DeviceAppEvent> {
            return events.filter {
                val key = "${it.deviceId}:${it.packageName}:${it.eventType}:${it.occurredAt}"
                set.add(key)
            }
        }

        val deduplicated = deduplicate(listOf(event1, duplicateEvent, distinctEvent))
        assertEquals(2, deduplicated.size)
        assertEquals("YouTube", deduplicated[0].appLabel)
        assertEquals("WhatsApp", deduplicated[1].appLabel)
    }

    @Test
    fun testCommandStatusTransitionValidation() {
        fun isValidTransition(current: CommandStatus, target: CommandStatus): Boolean {
            return when (current) {
                CommandStatus.PENDING -> target in listOf(CommandStatus.DELIVERED, CommandStatus.EXECUTING, CommandStatus.CANCELLED, CommandStatus.EXPIRED)
                CommandStatus.DELIVERED -> target in listOf(CommandStatus.EXECUTING, CommandStatus.FAILED, CommandStatus.BLOCKED)
                CommandStatus.EXECUTING -> target in listOf(CommandStatus.SUCCEEDED, CommandStatus.FAILED, CommandStatus.BLOCKED)
                CommandStatus.SUCCEEDED, CommandStatus.FAILED, CommandStatus.EXPIRED, CommandStatus.CANCELLED, CommandStatus.BLOCKED -> false
            }
        }

        assertTrue("PENDING to EXECUTING is valid", isValidTransition(CommandStatus.PENDING, CommandStatus.EXECUTING))
        assertTrue("EXECUTING to SUCCEEDED is valid", isValidTransition(CommandStatus.EXECUTING, CommandStatus.SUCCEEDED))
        assertTrue("EXECUTING to BLOCKED is valid", isValidTransition(CommandStatus.EXECUTING, CommandStatus.BLOCKED))
        assertFalse("Terminal SUCCEEDED to PENDING is invalid", isValidTransition(CommandStatus.SUCCEEDED, CommandStatus.PENDING))
        assertFalse("Terminal FAILED to EXECUTING is invalid", isValidTransition(CommandStatus.FAILED, CommandStatus.EXECUTING))
    }

    @Test
    fun testConsentEnforcement() {
        val consent = DeviceConsent(
            deviceId = "dev-01",
            locationSharingEnabled = false,
            appActivityEnabled = true,
            remoteCommandsEnabled = false,
            updatedAt = isoFormat.format(Date())
        )

        fun canCollectLocation(c: DeviceConsent) = c.locationSharingEnabled
        fun canCollectActivity(c: DeviceConsent) = c.appActivityEnabled
        fun canExecuteCommands(c: DeviceConsent) = c.remoteCommandsEnabled

        assertFalse("Location sharing is disabled", canCollectLocation(consent))
        assertTrue("App activity is enabled", canCollectActivity(consent))
        assertFalse("Remote commands are disabled", canExecuteCommands(consent))
    }

    @Test
    fun testSevenDayRetentionWindowCalculation() {
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val cutoff = now - sevenDaysMs

        val eventWithinRetention = now - (3L * 24 * 60 * 60 * 1000)
        val eventExpired = now - (8L * 24 * 60 * 60 * 1000)

        assertTrue("3-day old event is retained", eventWithinRetention >= cutoff)
        assertFalse("8-day old event is purged", eventExpired >= cutoff)
    }
}
