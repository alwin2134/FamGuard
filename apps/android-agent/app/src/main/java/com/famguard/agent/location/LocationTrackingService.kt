package com.famguard.agent.location

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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

    private var systemLocationManager: LocationManager? = null
    private var systemLocationListener: LocationListener? = null

    private var activeSessionId: String? = null
    private var sessionExpiresAtMs: Long = 0L
    private var lastUploadedLat: Double? = null
    private var lastUploadedLon: Double? = null
    private var bestAccuracySoFar: Float = Float.MAX_VALUE

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate() {
        super.onCreate()
        keystoreManager = DeviceKeystoreManager(this)
        apiClient = AgentApiClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        systemLocationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                android.util.Log.d("LocationService", "Fused location fix: ${loc.latitude}, ${loc.longitude} ±${loc.accuracy}m")
                handleNewLocation(loc)
            }
        }

        systemLocationListener = LocationListener { loc ->
            android.util.Log.d("LocationService", "Hardware GPS provider fix: ${loc.latitude}, ${loc.longitude} ±${loc.accuracy}m")
            handleNewLocation(loc)
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

        if (sessionId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // WhatsApp-style indefinite live location if expiresAtMs <= 0
        val effectiveExpiresAt = if (expiresAtMs <= 0 || expiresAtMs < System.currentTimeMillis()) {
            System.currentTimeMillis() + 1000L * 86400 * 365 * 10 // 10 years
        } else {
            expiresAtMs
        }

        activeSessionId = sessionId
        sessionExpiresAtMs = effectiveExpiresAt
        bestAccuracySoFar = Float.MAX_VALUE
        keystoreManager.saveActiveSession(sessionId, effectiveExpiresAt)

        // Start Foreground Service with location type
        val notification = buildPersistentNotification(effectiveExpiresAt)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "startForeground failed: ${e.message}")
        }

        startLocationUpdates()
        startExpiryWatcher()

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // High-Precision Location Configuration
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3_000L // Desired interval: 3 seconds (real-time stream)
        ).apply {
            setMinUpdateIntervalMillis(1_500L) // Fastest interval: 1.5 seconds
            setMinUpdateDistanceMeters(1.0f)   // Minimum movement: 1 meter
            setMaxUpdateDelayMillis(3_000L)    // Don't batch delay
        }.build()

        try {
            // Immediately request fresh GPS fix
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) {
                    handleNewLocation(loc)
                }
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    handleNewLocation(loc)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )

            // Register native hardware GPS as parallel provider
            systemLocationManager?.let { lm ->
                systemLocationListener?.let { listener ->
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            2_000L,
                            1.0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        android.util.Log.d("LocationService", "Hardware GPS_PROVIDER registered")
                    }
                    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            4_000L,
                            2.0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        android.util.Log.d("LocationService", "Hardware NETWORK_PROVIDER registered")
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("LocationService", "Location permission missing: ${e.message}")
            stopTracking("Location permission missing")
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Error requesting location updates: ${e.message}")
        }
    }

    private fun handleNewLocation(location: Location) {
        val sessionId = activeSessionId ?: return
        val deviceId = keystoreManager.getDeviceId() ?: return
        val deviceSecret = keystoreManager.getDeviceSecret() ?: return

        val isIndefinite = sessionExpiresAtMs <= 0 || sessionExpiresAtMs > System.currentTimeMillis() + 86400000L * 30
        if (!isIndefinite && System.currentTimeMillis() > sessionExpiresAtMs) {
            stopTracking("Session expired")
            return
        }

        // Accuracy Quality Filter:
        // Ignore wildly inaccurate fixes (> 500 meters, e.g. rough cell tower triangulation)
        // when we have already received better fixes.
        if (location.accuracy > 500f && bestAccuracySoFar < 100f) {
            android.util.Log.d("LocationService", "Dropped inaccurate fix: ±${location.accuracy}m")
            return
        }

        // Check distance moved from last uploaded position
        val lastLat = lastUploadedLat
        val lastLon = lastUploadedLon
        if (lastLat != null && lastLon != null) {
            val results = FloatArray(1)
            Location.distanceBetween(lastLat, lastLon, location.latitude, location.longitude, results)
            val distanceMoved = results[0]

            // If stationary (< 1.5m moved) and accuracy didn't noticeably improve, skip redundant upload
            val accuracyImproved = location.accuracy < (bestAccuracySoFar * 0.8f)
            if (distanceMoved < 1.5f && !accuracyImproved) {
                return
            }
        }

        if (location.accuracy < bestAccuracySoFar) {
            bestAccuracySoFar = location.accuracy
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
        val isIndefinite = sessionExpiresAtMs <= 0 || sessionExpiresAtMs > System.currentTimeMillis() + 86400000L * 30
        if (isIndefinite) return

        serviceScope.launch {
            while (isActive) {
                val remaining = sessionExpiresAtMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    stopTracking("Session expired")
                    break
                }
                delay(10_000L)
            }
        }
    }

    private fun stopTracking(reason: String) {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            systemLocationListener?.let { listener ->
                systemLocationManager?.removeUpdates(listener)
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Error removing updates: ${e.message}")
        }

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

        val isIndefinite = expiresAtMs <= 0 || expiresAtMs > System.currentTimeMillis() + 86400000L * 30
        val contentText = if (isIndefinite) {
            "Continuous live location sharing is active (WhatsApp style)"
        } else {
            val remainingMinutes = ((expiresAtMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
            "Sharing live location with guardian (~$remainingMinutes min left)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FamGuard Live Location Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Tracking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows persistent status while live location is being shared with family."
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            systemLocationListener?.let { listener ->
                systemLocationManager?.removeUpdates(listener)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SESSION_ID = "com.famguard.agent.SESSION_ID"
        const val EXTRA_EXPIRES_AT_MS = "com.famguard.agent.EXPIRES_AT_MS"
        const val ACTION_STOP_SESSION = "com.famguard.agent.ACTION_STOP_SESSION"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "famguard_location_channel"

        fun start(context: Context, sessionId: String, expiresAtMs: Long) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
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
