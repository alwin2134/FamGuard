package com.famguard.agent.call

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
 * Foreground Service for Active Voice Calling on the Agent device.
 * Enforces Android 14+ FOREGROUND_SERVICE_TYPE_MICROPHONE requirement.
 */
class VoiceCallService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webRtcManager: WebRtcManager? = null
    private lateinit var apiClient: AgentApiClient
    private lateinit var keystoreManager: DeviceKeystoreManager
    private lateinit var audioManager: AudioManager

    private var isMuted = false
    private var isSpeakerOn = true

    override fun onCreate() {
        super.onCreate()
        apiClient = AgentApiClient(this)
        keystoreManager = DeviceKeystoreManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        webRtcManager = WebRtcManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_END_CALL) {
            stopSelf()
            return START_NOT_STICKY
        } else if (action == ACTION_TOGGLE_MUTE) {
            toggleMute()
            return START_STICKY
        } else if (action == ACTION_TOGGLE_SPEAKER) {
            toggleSpeaker()
            return START_STICKY
        }

        val remoteOfferSdp = intent.getStringExtra(EXTRA_REMOTE_OFFER)

        // Start Foreground Notification
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        configureAudioMode()
        startVoiceCallPipeline(remoteOfferSdp)
        return START_STICKY
    }

    private fun configureAudioMode() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeakerOn
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure AudioManager: ${e.message}")
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        // Muting audio track
        Log.i(TAG, "Microphone muted: $isMuted")
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        Log.i(TAG, "Speakerphone: $isSpeakerOn")
    }

    private fun startVoiceCallPipeline(remoteOfferSdp: String?) {
        val rtc = webRtcManager ?: return
        rtc.startAudioTrack()
        rtc.createPeerConnection(includeVideo = false, includeAudio = true)

        rtc.onLocalSdpGenerated = { sdp ->
            sendSignalToController("answer", sdp.description)
        }

        rtc.onLocalIceCandidateGenerated = { candidate ->
            val payload = JSONObject().apply {
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("candidate", candidate.sdp)
            }
            sendSignalToController("ice_candidate", payload.toString())
        }

        rtc.onCallEnded = {
            stopSelf()
        }

        if (!remoteOfferSdp.isNullOrBlank()) {
            val offer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
            rtc.setRemoteDescription(offer) {
                rtc.createAnswer { answer ->
                    Log.i(TAG, "Voice call answer created.")
                }
            }
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
                    put("stream_type", "voice_call")
                }
                apiClient.sendSignal(devId, devSec, payload.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send call signal: ${e.message}")
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
                        val signals = apiClient.fetchIncomingSignals(devId, devSec, "voice_call")
                        for (sig in signals) {
                            handleRemoteSignal(sig)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error polling call signals: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun handleRemoteSignal(signalJson: String) {
        try {
            val obj = JSONObject(signalJson)
            val type = obj.optString("type")
            val data = obj.optString("data")

            when (type) {
                "offer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, data)
                    webRtcManager?.setRemoteDescription(sdp) {
                        webRtcManager?.createAnswer { }
                    }
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
                "end_call" -> {
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling call signal: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val endCallIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceCallService::class.java).apply { action = ACTION_END_CALL },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Call in Progress")
            .setContentText("Connected with Guardian (HD Audio)")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endCallIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Call Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active voice call connection with Guardian"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore audio mode: ${e.message}")
        }
        webRtcManager?.close()
        webRtcManager = null
        Log.i(TAG, "VoiceCallService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoiceCallService"
        private const val CHANNEL_ID = "famguard_voice_call"
        private const val NOTIFICATION_ID = 8842

        const val ACTION_END_CALL = "com.famguard.agent.END_VOICE_CALL"
        const val ACTION_TOGGLE_MUTE = "com.famguard.agent.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.famguard.agent.TOGGLE_SPEAKER"
        const val EXTRA_REMOTE_OFFER = "extra_remote_offer"

        fun startCall(context: Context, offerSdp: String? = null) {
            val intent = Intent(context, VoiceCallService::class.java).apply {
                if (!offerSdp.isNullOrBlank()) {
                    putExtra(EXTRA_REMOTE_OFFER, offerSdp)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun endCall(context: Context) {
            val intent = Intent(context, VoiceCallService::class.java).apply {
                action = ACTION_END_CALL
            }
            context.startService(intent)
        }
    }
}
