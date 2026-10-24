package com.famguard.agent.activity

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.famguard.contracts.AppEventType
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class RawUsageEvent(
    val packageName: String,
    val appLabel: String?,
    val eventType: AppEventType,
    val timestampMs: Long,
    val isoTimestamp: String
)

class UsageEventReader(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = context.packageManager
    private val labelCache = ConcurrentHashMap<String, String>()
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Reads usage events within the bounded window [startTimeMs, endTimeMs].
     * Excludes system noise, filters to foreground/background transitions.
     */
    fun readEvents(startTimeMs: Long, endTimeMs: Long): List<RawUsageEvent> {
        val usm = usageStatsManager ?: return emptyList()
        if (startTimeMs >= endTimeMs) return emptyList()

        val results = mutableListOf<RawUsageEvent>()
        val events = usm.queryEvents(startTimeMs, endTimeMs)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            // Filter out system launchers or empty packages
            if (pkg == "android" || pkg == context.packageName) continue

            val eventType: AppEventType? = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> AppEventType.FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> AppEventType.BACKGROUND
                else -> null
            }

            if (eventType != null) {
                val label = getAppLabel(pkg)
                val isoDate = isoFormat.format(Date(event.timeStamp))
                results.add(
                    RawUsageEvent(
                        packageName = pkg,
                        appLabel = label,
                        eventType = eventType,
                        timestampMs = event.timeStamp,
                        isoTimestamp = isoDate
                    )
                )
            }
        }

        return results
    }

    private fun getAppLabel(packageName: String): String {
        return labelCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }
}
