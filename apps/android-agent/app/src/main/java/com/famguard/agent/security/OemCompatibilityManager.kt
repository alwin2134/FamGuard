package com.famguard.agent.security

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

class OemCompatibilityManager(private val context: Context) {

    companion object {
        private const val TAG = "OemCompatibility"
    }

    val manufacturer: String = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val model: String = Build.MODEL

    val isVivoOrIqoo: Boolean = manufacturer.contains("vivo") || manufacturer.contains("iqoo")
    val isXiaomi: Boolean = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
    val isOppoOrRealme: Boolean = manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")
    val isSamsung: Boolean = manufacturer.contains("samsung")
    val isHuawei: Boolean = manufacturer.contains("huawei") || manufacturer.contains("honor")

    val isCustomOem: Boolean = isVivoOrIqoo || isXiaomi || isOppoOrRealme || isSamsung || isHuawei

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun getOemDisplayName(): String {
        return when {
            isVivoOrIqoo -> "iQOO / Vivo (Funtouch / OriginOS)"
            isXiaomi -> "Xiaomi / Redmi / POCO (MIUI / HyperOS)"
            isOppoOrRealme -> "Oppo / Realme / OnePlus (ColorOS / OxygenOS)"
            isSamsung -> "Samsung (One UI)"
            isHuawei -> "Huawei / Honor (EMUI / MagicOS)"
            else -> "${Build.MANUFACTURER} Android"
        }
    }

    fun getOemActionInstructions(): List<String> {
        return when {
            isVivoOrIqoo -> listOf(
                "1. Autostart: Turn ON FamGuard and enable 'Associated startup'",
                "2. Battery: Select 'Allow high background power consumption' (avoids sleep freezes)",
                "3. Background Pop-ups: Grant 'Display pop-up windows while running in background' (required for App Lock overlay)",
                "4. Recent Apps: In multitasking screen, lock FamGuard with the 🔒 icon so it's not force-killed"
            )
            isXiaomi -> listOf(
                "1. Autostart: Enable Autostart in Security Settings",
                "2. Battery Saver: Set FamGuard to 'No restrictions'",
                "3. Other Permissions: Grant 'Display pop-up windows while running in background'",
                "4. Recent Apps: Lock FamGuard with the padlock in Recents screen"
            )
            isOppoOrRealme -> listOf(
                "1. Auto-launch: Enable 'Allow auto-launch' & 'Allow secondary launch'",
                "2. Battery: Enable 'Allow background activity' and disable power saver freezing",
                "3. Floating Windows: Grant 'Display over other apps'",
                "4. Recent Apps: Lock FamGuard in the multitasking tray"
            )
            isSamsung -> listOf(
                "1. Battery: Go to App info ➔ Battery ➔ Select 'Unrestricted'",
                "2. Sleeping Apps: Ensure FamGuard is NOT in 'Deep sleeping apps'",
                "3. Appear on top: Ensure 'Appear on top' is turned ON for App Lock"
            )
            else -> listOf(
                "1. Battery Optimization: Disable battery optimization ('Unrestricted')",
                "2. Appear on top: Allow 'Display over other apps' so App Lock works"
            )
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct ignore battery optimizations request failed, falling back to settings list: ${e.message}")
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings: ${e2.message}")
                openAppDetailsSettings()
            }
        }
    }

    fun openOverlaySettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay settings: ${e.message}")
            openAppDetailsSettings()
        }
    }

    fun openAutostartSettings() {
        val intents = mutableListOf<Intent>()

        when {
            isVivoOrIqoo -> {
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")))
            }
            isXiaomi -> {
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            }
            isOppoOrRealme -> {
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListAct")))
            }
            isHuawei -> {
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
            }
        }

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched OEM autostart intent: ${intent.component}")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Intent failed: ${intent.component?.className}: ${e.message}")
            }
        }

        // Fallback to app details or power settings
        requestIgnoreBatteryOptimizations()
    }

    fun openBackgroundPopupSettings() {
        val intents = mutableListOf<Intent>()

        if (isVivoOrIqoo) {
            intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
            intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")))
            intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
        } else if (isXiaomi) {
            intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            })
        }

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched background popup intent: ${intent.component}")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Background popup intent failed: ${e.message}")
            }
        }

        // Fallback to standard overlay permission
        openOverlaySettings()
    }

    fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings: ${e.message}")
        }
    }
}
