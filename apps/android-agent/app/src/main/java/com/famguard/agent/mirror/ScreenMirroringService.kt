package com.famguard.agent.mirror

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.famguard.agent.MainActivity
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.agent.webrtc.WebRtcManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Foreground Service that captures the screen and streams it to the controller via WebRTC.
 * Enforces Android 14+ FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION requirement.
 */
class ScreenMirroringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webRtcManager: WebRtcManager? = null
    private lateinit var apiClient: AgentApiClient
    private lateinit var keystoreManager: DeviceKeystoreManager

    override fun onCreate() {
        super.onCreate()
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

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Invalid MediaProjection result or data. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Notification
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startMirroringPipeline(resultData)
        return START_STICKY
    }

    private fun startMirroringPipeline(permissionResultData: Intent) {
        val rtc = webRtcManager ?: return
        rtc.startScreenCapture(permissionResultData)
        rtc.createPeerConnection(includeVideo = true, includeAudio = false)

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

        rtc.createOffer { sdp ->
            Log.i(TAG, "Screen mirror offer created successfully.")
        }

        startSignalListeningLoop()
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
            Log.e(TAG, "Error handling signal: ${e.message}")
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
            Intent(this, ScreenMirroringService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Live screen is being securely streamed to Guardian")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mirror", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Mirroring Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies child when live screen mirroring is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        webRtcManager?.close()
        webRtcManager = null
        Log.i(TAG, "ScreenMirroringService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenMirroringService"
        private const val CHANNEL_ID = "famguard_screen_mirror"
        private const val NOTIFICATION_ID = 8841

        const val ACTION_STOP = "com.famguard.agent.STOP_SCREEN_MIRROR"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenMirroringService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ScreenMirroringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
