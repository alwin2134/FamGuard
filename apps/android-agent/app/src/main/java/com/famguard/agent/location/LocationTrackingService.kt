package com.famguard.agent.location

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.famguard.agent.MainActivity
import com.famguard.agent.R
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.contracts.UploadLocationRequest
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var keystoreManager: DeviceKeystoreManager
    private lateinit var apiClient: AgentApiClient

    private var activeSessionId: String? = null
    private var sessionExpiresAtMs: Long = 0L
    private var lastUploadedLat: Double? = null
    private var lastUploadedLon: Double? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate() {
        super.onCreate()
        keystoreManager = DeviceKeystoreManager(this)
        apiClient = AgentApiClient()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleNewLocation(loc)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SESSION) {
            stopTracking("Stopped by user from notification")
            return START_NOT_STICKY
        }

        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val expiresAtMs = intent?.getLongExtra(EXTRA_EXPIRES_AT_MS, 0L) ?: 0L

        if (sessionId.isNullOrBlank() || expiresAtMs <= System.currentTimeMillis()) {
            stopSelf()
            return START_NOT_STICKY
        }

        activeSessionId = sessionId
        sessionExpiresAtMs = expiresAtMs
        keystoreManager.saveActiveSession(sessionId, expiresAtMs)

        // Start Foreground Service with location type
        val notification = buildPersistentNotification(expiresAtMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        startExpiryWatcher()

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            12_000L // Desired interval: ~12 seconds
        ).apply {
            setMinUpdateIntervalMillis(8_000L) // Fastest interval: 8 seconds
            setMinUpdateDistanceMeters(5.0f) // Minimum distance threshold: 5 meters
            setMaxUpdateDelayMillis(20_000L) // Batching allowance to preserve battery
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopTracking("Location permission missing")
        }
    }

    private fun handleNewLocation(location: Location) {
        val sessionId = activeSessionId ?: return
        val deviceId = keystoreManager.getDeviceId() ?: return
        val deviceSecret = keystoreManager.getDeviceSecret() ?: return

        if (System.currentTimeMillis() > sessionExpiresAtMs) {
            stopTracking("Session expired")
            return
        }

        // Avoid unnecessary duplicate uploads if moved less than 5m and accuracy didn't improve
        val lastLat = lastUploadedLat
        val lastLon = lastUploadedLon
        if (lastLat != null && lastLon != null) {
            val results = FloatArray(1)
            Location.distanceBetween(lastLat, lastLon, location.latitude, location.longitude, results)
            val distanceMoved = results[0]
            if (distanceMoved < 3.0f && location.accuracy > 20.0f) {
                // Insignificant change, save radio power
                return
            }
        }

        lastUploadedLat = location.latitude
        lastUploadedLon = location.longitude
        keystoreManager.updateLastLocationFix(System.currentTimeMillis())

        val recordedAtIso = isoFormat.format(Date(location.time))

        serviceScope.launch {
            val uploadResult = apiClient.uploadLocation(
                UploadLocationRequest(
                    deviceId = deviceId,
                    deviceSecret = deviceSecret,
                    sessionId = sessionId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = location.accuracy,
                    recordedAt = recordedAtIso
                )
            )

            uploadResult.onFailure { err ->
                if (err.message?.contains("expired", ignoreCase = true) == true ||
                    err.message?.contains("revoked", ignoreCase = true) == true
                ) {
                    stopTracking("Server rejected update: ${err.message}")
                }
            }
        }
    }

    private fun startExpiryWatcher() {
        serviceScope.launch {
            while (isActive) {
                val remaining = sessionExpiresAtMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    stopTracking("Session expired")
                    break
                }
                delay(10_000L) // Check every 10 seconds
            }
        }
    }

    private fun stopTracking(reason: String) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        keystoreManager.saveActiveSession(null, null)

        val sessId = activeSessionId
        val devId = keystoreManager.getDeviceId()
        val devSec = keystoreManager.getDeviceSecret()

        if (sessId != null && devId != null && devSec != null) {
            serviceScope.launch {
                apiClient.stopLocationSession(sessId, devId, devSec, reason)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildPersistentNotification(expiresAtMs: Long): Notification {
        val remainingMinutes = ((expiresAtMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FamGuard Live Location Active")
            .setContentText("Sharing live location with authorized family (~$remainingMinutes min left)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_media_pause, "Stop Sharing", pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Sharing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows persistent status while active live location sharing is in progress"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "famguard_location_channel"
        const val NOTIFICATION_ID = 4001
        const val ACTION_START_SESSION = "com.famguard.agent.action.START_LOCATION_SESSION"
        const val ACTION_STOP_SESSION = "com.famguard.agent.action.STOP_LOCATION_SESSION"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_EXPIRES_AT_MS = "extra_expires_at_ms"

        fun start(context: Context, sessionId: String, expiresAtMs: Long) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_EXPIRES_AT_MS, expiresAtMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
            context.startService(intent)
        }
    }
}
