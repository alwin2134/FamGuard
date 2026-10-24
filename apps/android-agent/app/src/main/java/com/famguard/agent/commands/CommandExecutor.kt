package com.famguard.agent.commands

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.location.LocationTrackingService
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.contracts.CommandAction
import com.famguard.contracts.CommandStatus
import com.famguard.contracts.DeviceCommand
import com.famguard.contracts.DeviceCommandAckRequest
import kotlinx.coroutines.*
import org.json.JSONObject

class CommandExecutor(
    private val context: Context,
    private val apiClient: AgentApiClient = AgentApiClient(),
    private val keystoreManager: DeviceKeystoreManager = DeviceKeystoreManager(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeRingtone: Ringtone? = null

    suspend fun executeCommand(command: DeviceCommand): CommandStatus {
        val deviceId = keystoreManager.getDeviceId() ?: return CommandStatus.FAILED
        val deviceSecret = keystoreManager.getDeviceSecret() ?: return CommandStatus.FAILED

        // First ack as EXECUTING
        apiClient.ackCommand(
            DeviceCommandAckRequest(
                commandId = command.id,
                deviceId = deviceId,
                deviceSecret = deviceSecret,
                status = CommandStatus.EXECUTING
            )
        )

        var resultStatus: CommandStatus = CommandStatus.FAILED
        var failureReason: String? = null

        when (command.action) {
            CommandAction.REFRESH_LOCATION -> {
                try {
                    val payload = command.payloadJson?.let { JSONObject(it) }
                    val sessionId = payload?.optString("session_id")
                    val expiresAtIso = payload?.optString("expires_at")

                    if (!sessionId.isNullOrBlank()) {
                        val expiresAtMs = if (!expiresAtIso.isNullOrBlank()) {
                            // Convert ISO to millis or fallback to 5 minutes
                            System.currentTimeMillis() + 5 * 60 * 1000
                        } else {
                            System.currentTimeMillis() + 5 * 60 * 1000
                        }
                        LocationTrackingService.start(context, sessionId, expiresAtMs)
                        resultStatus = CommandStatus.SUCCEEDED
                    } else {
                        resultStatus = CommandStatus.SUCCEEDED
                    }
                } catch (e: Exception) {
                    resultStatus = CommandStatus.FAILED
                    failureReason = e.message
                }
            }

            CommandAction.RING -> {
                try {
                    playRingAlert()
                    resultStatus = CommandStatus.SUCCEEDED
                } catch (e: Exception) {
                    resultStatus = CommandStatus.FAILED
                    failureReason = e.message
                }
            }
        }

        // Send final ACK to server
        apiClient.ackCommand(
            DeviceCommandAckRequest(
                commandId = command.id,
                deviceId = deviceId,
                deviceSecret = deviceSecret,
                status = resultStatus,
                failureReason = failureReason
            )
        )

        return resultStatus
    }

    private fun playRingAlert() {
        val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val ringtone = RingtoneManager.getRingtone(context, alertUri)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
        }

        activeRingtone = ringtone
        ringtone?.play()

        // Vibrate
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400, 800), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 800, 400, 800), -1)
        }

        // Show audible alert notification with dismiss
        showRingNotification()

        // Auto silence after 15 seconds
        scope.launch {
            delay(15_000L)
            silenceAlert()
        }
    }

    fun silenceAlert() {
        activeRingtone?.stop()
        activeRingtone = null
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(RING_NOTIFICATION_ID)
    }

    private fun showRingNotification() {
        val channelId = "famguard_commands_channel"
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Family Device Controls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Audible ring alerts and remote commands"
            }
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("FamGuard Device Alert")
            .setContentText("A family guardian triggered an audible ring alert on this device.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        manager?.notify(RING_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val RING_NOTIFICATION_ID = 5002
    }
}
