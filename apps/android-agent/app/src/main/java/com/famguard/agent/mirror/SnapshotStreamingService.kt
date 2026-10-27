package com.famguard.agent.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.famguard.agent.MainActivity
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.agent.service.FamGuardAccessibilityService
import com.famguard.agent.webrtc.WebRtcManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Foreground Service for Prompt-Free Rapid Snapshot Streaming (Option 3).
 * Uses FamGuardAccessibilityService.takeScreenshot() to capture silent screen frames
 * at ~4-5 FPS and transmits them to Controller via WebRTC DataChannel (with signaling bootstrap).
 */
class SnapshotStreamingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webRtcManager: WebRtcManager? = null
    private lateinit var apiClient: AgentApiClient
    private lateinit var keystoreManager: DeviceKeystoreManager
    private var lastSignalFrameTime = 0L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        apiClient = AgentApiClient(this)
        keystoreManager = DeviceKeystoreManager(this)
        webRtcManager = WebRtcManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Service with dataSync type
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startStreamingPipeline()
        return START_STICKY
    }

    private fun startStreamingPipeline() {
        val rtc = webRtcManager ?: return

        // Create PeerConnection (DataChannel only, no MediaProjection video track required)
        rtc.createPeerConnection(includeVideo = false, includeAudio = false)

        // Create DataChannel for binary frame streaming
        rtc.createDataChannel("screen_stream")

        rtc.onLocalSdpGenerated = { sdp ->
            sendSignalToController("offer", sdp.description)
        }

        rtc.onLocalIceCandidateGenerated = { candidate ->
            val payload = JSONObject().apply {
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("candidate", candidate.sdp)
            }
            sendSignalToController("ice_candidate", payload.toString())
        }

        // Generate Offer
        rtc.createOffer {
            Log.i(TAG, "Snapshot streaming WebRTC offer created.")
        }

        // Start signal poller for Controller's Answer / ICE
        startSignalListeningLoop()

        // Start silent snapshot capture & streaming loop
        startSnapshotCaptureLoop()
    }

    private fun startSnapshotCaptureLoop() {
        serviceScope.launch(Dispatchers.Default) {
            Log.i(TAG, "Starting prompt-free snapshot capture loop.")
            while (isActive) {
                val accessibility = FamGuardAccessibilityService.instance
                if (accessibility == null) {
                    Log.w(TAG, "FamGuardAccessibilityService not running. Awaiting accessibility activation...")
                    // Inform controller accessibility is required
                    if (System.currentTimeMillis() - lastSignalFrameTime > 4000) {
                        lastSignalFrameTime = System.currentTimeMillis()
                        sendSignalToController("status", "waiting_for_accessibility")
                    }
                    delay(1000)
                    continue
                }

                val frameStartTime = System.currentTimeMillis()
                val screenshotBitmap = captureScreenshotSuspend(accessibility)

                if (screenshotBitmap != null) {
                    try {
                        // Downscale to 540 width maintaining aspect ratio for ultra-fast compression & low bandwidth
                        val targetWidth = 540
                        val targetHeight = (screenshotBitmap.height * (540f / screenshotBitmap.width)).toInt()
                        val scaled = Bitmap.createScaledBitmap(screenshotBitmap, targetWidth, targetHeight, true)
                        if (scaled !== screenshotBitmap) {
                            screenshotBitmap.recycle()
                        }

                        val stream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 65, stream)
                        val frameBytes = stream.toByteArray()
                        scaled.recycle()

                        // 1. Primary real-time pipeline: Send directly over WebRTC DataChannel
                        val sentViaDataChannel = webRtcManager?.sendData(frameBytes) ?: false

                        // 2. Secondary bootstrap: Send initial frame or periodic keepalive frame via signaling
                        // until WebRTC DataChannel enters OPEN state
                        if (!sentViaDataChannel && (System.currentTimeMillis() - lastSignalFrameTime > 2000)) {
                            lastSignalFrameTime = System.currentTimeMillis()
                            val base64Frame = Base64.encodeToString(frameBytes, Base64.NO_WRAP)
                            sendSignalToController("screen_frame", base64Frame)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error encoding snapshot frame: ${e.message}")
                    }
                }

                val elapsed = System.currentTimeMillis() - frameStartTime
                // Target ~4 to 5 FPS (200-250ms cadence)
                val delayTime = (220 - elapsed).coerceAtLeast(80)
                delay(delayTime)
            }
        }
    }

    private suspend fun captureScreenshotSuspend(accessibility: FamGuardAccessibilityService): Bitmap? =
        suspendCancellableCoroutine { cont ->
            accessibility.captureScreenshot { bitmap ->
                if (cont.isActive) {
                    cont.resume(bitmap)
                } else {
                    bitmap?.recycle()
                }
            }
        }

    private fun sendSignalToController(type: String, data: String) {
        serviceScope.launch {
            try {
                val devId = keystoreManager.getDeviceId() ?: return@launch
                val devSec = keystoreManager.getDeviceSecret() ?: return@launch
                val payload = JSONObject().apply {
                    put("type", type)
                    put("data", data)
                    put("stream_type", "screen_mirror")
                }
                apiClient.sendSignal(devId, devSec, payload.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send WebRTC signal: ${e.message}")
            }
        }
    }

    private fun startSignalListeningLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val devId = keystoreManager.getDeviceId()
                    val devSec = keystoreManager.getDeviceSecret()
                    if (devId != null && devSec != null) {
                        val signals = apiClient.fetchIncomingSignals(devId, devSec, "screen_mirror")
                        for (sig in signals) {
                            handleRemoteSignal(sig)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error polling signals: ${e.message}")
                }
                delay(1200)
            }
        }
    }

    private fun handleRemoteSignal(signalJson: String) {
        try {
            val obj = JSONObject(signalJson)
            val type = obj.optString("type")
            val data = obj.optString("data")

            when (type) {
                "answer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, data)
                    webRtcManager?.setRemoteDescription(sdp)
                }
                "ice_candidate" -> {
                    val candObj = JSONObject(data)
                    val candidate = IceCandidate(
                        candObj.getString("sdpMid"),
                        candObj.getInt("sdpMLineIndex"),
                        candObj.getString("candidate")
                    )
                    webRtcManager?.addIceCandidate(candidate)
                }
                "stop" -> {
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling signal in SnapshotStreamingService: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SnapshotStreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Protection Active")
            .setContentText("Silent live screen monitoring is active for Guardian")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies child when screen monitoring protection is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        webRtcManager?.close()
        webRtcManager = null
        Log.i(TAG, "SnapshotStreamingService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SnapshotStreamService"
        private const val CHANNEL_ID = "famguard_snapshot_stream"
        private const val NOTIFICATION_ID = 8842

        const val ACTION_STOP = "com.famguard.agent.STOP_SNAPSHOT_STREAM"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, SnapshotStreamingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SnapshotStreamingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
