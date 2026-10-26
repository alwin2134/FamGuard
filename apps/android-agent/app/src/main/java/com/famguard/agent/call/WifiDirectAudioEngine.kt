package com.famguard.agent.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 100% Offline Wi-Fi Direct (P2P) Walkie-Talkie Audio Engine.
 * Operates without Wi-Fi router, cellular towers, or internet connection.
 */
class WifiDirectAudioEngine(private val context: Context) {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isStreaming = false
    private var socket: DatagramSocket? = null

    var onStatusChanged: ((String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startP2pDiscovery() {
        if (manager == null || channel == null) {
            onStatusChanged?.invoke("Wi-Fi Direct not supported on this device.")
            return
        }

        onStatusChanged?.invoke("Scanning for nearby device via Wi-Fi Direct...")
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatusChanged?.invoke("Wi-Fi Direct discovery active. Listening for connection...")
            }

            override fun onFailure(reason: Int) {
                onStatusChanged?.invoke("P2P discovery failed: error code $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        onStatusChanged?.invoke("Connecting to ${device.deviceName}...")
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatusChanged?.invoke("Wi-Fi P2P connection initiated.")
            }

            override fun onFailure(reason: Int) {
                onStatusChanged?.invoke("P2P connection failed: $reason")
            }
        })
    }

    fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (info.groupFormed) {
            val isOwner = info.isGroupOwner
            val targetIp = if (isOwner) {
                // Client will send packets first or we listen on 0.0.0.0
                null
            } else {
                info.groupOwnerAddress?.hostAddress
            }
            onStatusChanged?.invoke("P2P Group Formed! Starting Walkie-Talkie Audio...")
            onConnected?.invoke()
            startAudioStream(targetIp)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioStream(targetHost: String?) {
        if (isStreaming) return
        isStreaming = true

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        scope.launch {
            try {
                socket = DatagramSocket(PORT)
                socket?.broadcast = true

                // Start Receiver Coroutine
                launch {
                    val track = AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                    track.play()
                    val recvBuffer = ByteArray(bufferSize)

                    while (isActive && isStreaming) {
                        val packet = DatagramPacket(recvBuffer, recvBuffer.size)
                        socket?.receive(packet)
                        track.write(packet.data, packet.offset, packet.length)
                    }
                    track.stop()
                    track.release()
                }

                // Start Sender Coroutine
                launch {
                    val recorder = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    recorder.startRecording()
                    val sendBuffer = ByteArray(bufferSize)
                    val destAddress = targetHost?.let { InetAddress.getByName(it) } ?: InetAddress.getByName("192.168.49.255")

                    while (isActive && isStreaming) {
                        val bytesRead = recorder.read(sendBuffer, 0, sendBuffer.size)
                        if (bytesRead > 0) {
                            val packet = DatagramPacket(sendBuffer, bytesRead, destAddress, PORT)
                            socket?.send(packet)
                        }
                    }
                    recorder.stop()
                    recorder.release()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Audio streaming exception: ${e.message}", e)
                onStatusChanged?.invoke("P2P Audio Error: ${e.message}")
            }
        }
    }

    fun stop() {
        isStreaming = false
        scope.cancel()
        socket?.close()
        socket = null
        if (manager != null && channel != null) {
            manager.removeGroup(channel, null)
        }
        onStatusChanged?.invoke("P2P Walkie-Talkie stopped.")
    }

    companion object {
        private const val TAG = "WifiDirectAudio"
        private const val PORT = 8988
    }
}
