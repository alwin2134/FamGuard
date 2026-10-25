package com.famguard.agent.security

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.famguard.contracts.InstalledAppDto

object InstalledAppsProvider {
    /**
     * Enumerates all launchable user-facing applications installed on the device.
     */
    fun getLaunchableApps(context: Context): List<InstalledAppDto> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString() ?: pkg
                val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                InstalledAppDto(
                    packageName = pkg,
                    appLabel = label,
                    isSystem = isSystem
                )
            }.distinctBy { it.packageName }.sortedBy { it.appLabel.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
