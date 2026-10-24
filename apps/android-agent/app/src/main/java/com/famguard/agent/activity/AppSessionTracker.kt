package com.famguard.agent.activity

import com.famguard.contracts.AppEventType
import java.util.UUID

data class ProcessedAppEvent(
    val entity: PendingAppEventEntity,
    val durationSeconds: Long?
)

class AppSessionTracker {

    /**
     * Correlates foreground and background raw events to compute session durations where possible.
     * When a background event is missing, preserves the foreground event with null duration.
     */
    fun processEvents(rawEvents: List<RawUsageEvent>): List<PendingAppEventEntity> {
        val sorted = rawEvents.sortedBy { it.timestampMs }
        val result = mutableListOf<PendingAppEventEntity>()

        // Map of packageName -> last foreground event timestamp
        val openForegroundSessions = mutableMapOf<String, RawUsageEvent>()

        for (event in sorted) {
            when (event.eventType) {
                AppEventType.FOREGROUND -> {
                    openForegroundSessions[event.packageName] = event
                    result.add(
                        PendingAppEventEntity(
                            id = UUID.randomUUID().toString(),
                            packageName = event.packageName,
                            appLabel = event.appLabel,
                            eventType = "foreground",
                            occurredAt = event.isoTimestamp,
                            durationSeconds = null,
                            source = "android_usage_events"
                        )
                    )
                }
                AppEventType.BACKGROUND -> {
                    val matchingStart = openForegroundSessions.remove(event.packageName)
                    val durationSeconds = if (matchingStart != null && event.timestampMs >= matchingStart.timestampMs) {
                        (event.timestampMs - matchingStart.timestampMs) / 1000
                    } else null

                    result.add(
                        PendingAppEventEntity(
                            id = UUID.randomUUID().toString(),
                            packageName = event.packageName,
                            appLabel = event.appLabel,
                            eventType = "background",
                            occurredAt = event.isoTimestamp,
                            durationSeconds = durationSeconds,
                            source = "android_usage_events"
                        )
                    )
                }
                else -> Unit
            }
        }

        return result
    }
}
