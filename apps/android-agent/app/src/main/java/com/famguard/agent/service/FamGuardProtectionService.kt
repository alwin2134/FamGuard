package com.famguard.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.famguard.agent.MainActivity
import com.famguard.agent.activity.ActivityRepository
import com.famguard.agent.activity.AgentDatabase
import com.famguard.agent.activity.UsageAccessManager
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.commands.CommandExecutor
import com.famguard.agent.security.AppLockManager
import com.famguard.agent.security.AppLockOverlayManager
import com.famguard.agent.security.DeviceKeystoreManager
import kotlinx.coroutines.*

class FamGuardProtectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var keystoreManager: DeviceKeystoreManager
    private lateinit var apiClient: AgentApiClient
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var appLockManager: AppLockManager
    private lateinit var appLockOverlayManager: AppLockOverlayManager
    private lateinit var usageAccessManager: UsageAccessManager
    private lateinit var activityRepository: ActivityRepository

    private var currentForegroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        keystoreManager = DeviceKeystoreManager(this)
        apiClient = AgentApiClient()
        appLockManager = AppLockManager(this)
        appLockOverlayManager = AppLockOverlayManager(this, appLockManager)
        commandExecutor = CommandExecutor(this, apiClient, keystoreManager, appLockManager)
        usageAccessManager = UsageAccessManager(this)
        activityRepository = ActivityRepository(
            context = this,
            database = AgentDatabase.getInstance(this),
            keystoreManager = keystoreManager,
            apiClient = apiClient
        )

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        startCommandPollingLoop()
        startAutomaticActivitySyncLoop()
        startAppLockMonitorLoop()

        android.util.Log.d(TAG, "FamGuardProtectionService started in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep running continuously even if killed or app swiped away
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        appLockOverlayManager.dismissOverlay()
        android.util.Log.d(TAG, "FamGuardProtectionService destroyed")
    }

    /**
     * 1. Continuous Command Polling Loop (every 2.5s)
     * Keeps listening for commands from the Controller in real-time.
     */
    private fun startCommandPollingLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val deviceId = keystoreManager.getDeviceId()
                    val deviceSecret = keystoreManager.getDeviceSecret()

                    if (deviceId != null && deviceSecret != null) {
                        val pendingRes = apiClient.fetchPendingCommands(deviceId, deviceSecret)
                        pendingRes.onSuccess { commands ->
                            for (cmd in commands) {
                                android.util.Log.d(TAG, "Received remote command in background: ${cmd.action}")
                                commandExecutor.executeCommand(cmd)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error polling commands: ${e.message}")
                }
                delay(2500)
            }
        }
    }

    /**
     * 2. Automatic & Real-Time Activity Ingestion Loop (every 20s)
     * Automatically syncs foreground/background app transitions without manual clicks.
     */
    private fun startAutomaticActivitySyncLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (keystoreManager.isPaired() && usageAccessManager.hasUsageAccess()) {
                        activityRepository.collectAndQueueEvents()
                        activityRepository.syncPendingEventsToServer()
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error in automatic activity sync: ${e.message}")
                }
                delay(20_000)
            }
        }
    }

    /**
     * 3. App Lock & Overlay Monitor Loop (every 1s)
     * Detects top foreground app and displays lock overlay if restricted.
     */
    private fun startAppLockMonitorLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (usageAccessManager.hasUsageAccess()) {
                        val topPkg = detectTopForegroundPackage()
                        if (topPkg != null && topPkg != currentForegroundPackage) {
                            currentForegroundPackage = topPkg
                        }

                        if (topPkg != null && appLockManager.isPackageLocked(topPkg)) {
                            if (!appLockOverlayManager.isShowing(topPkg)) {
                                android.util.Log.d(TAG, "Locked app detected in foreground: $topPkg. Showing overlay.")
                                appLockOverlayManager.showOverlay(topPkg)
                            }
                        } else {
                            if (appLockOverlayManager.isShowing()) {
                                appLockOverlayManager.dismissOverlay()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error monitoring app locks: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun detectTopForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now)
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTs) {
                    latestTs = event.timeStamp
                    latestPkg = event.packageName
                }
            }
        }

        // Ignore our own app package or Android core
        if (latestPkg == packageName || latestPkg == "android") {
            return null
        }

        return latestPkg ?: currentForegroundPackage
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FamGuard Continuous Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps parental safety controls and activity sync active in the background"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FamGuard Protection Active")
            .setContentText("Monitoring family safety rules & real-time sync")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "FamGuardProtectionService"
        private const val CHANNEL_ID = "famguard_protection_channel"
        private const val NOTIFICATION_ID = 9001

        fun startService(context: Context) {
            val intent = Intent(context, FamGuardProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FamGuardProtectionService::class.java)
            context.stopService(intent)
        }
    }
}
