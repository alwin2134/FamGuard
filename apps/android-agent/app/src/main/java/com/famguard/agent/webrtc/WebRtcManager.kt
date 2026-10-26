package com.famguard.agent.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * WebRTC Manager for Agent app.
 * Handles PeerConnection, ScreenCapturerAndroid (for Screen Mirroring),
 * and AudioTrack (for Voice Calling).
 */
class WebRtcManager(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    // Video & Screen Capture
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null

    // Audio
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    var onLocalSdpGenerated: ((SessionDescription) -> Unit)? = null
    var onLocalIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun startScreenCapture(permissionResultData: Intent, width: Int = 720, height: Int = 1280, fps: Int = 30) {
        val factory = peerConnectionFactory ?: return
        try {
            screenCapturer = ScreenCapturerAndroid(permissionResultData, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by Android system.")
                    stopScreenCapture()
                }
            })

            videoSource = factory.createVideoSource(screenCapturer!!.isScreencast)
            val surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
            screenCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            screenCapturer?.startCapture(width, height, fps)

            localVideoTrack = factory.createVideoTrack("AGENT_SCREEN_TRACK", videoSource)
            localVideoTrack?.setEnabled(true)
            Log.i(TAG, "Screen capturer initialized and started capturing at ${width}x${height} @ ${fps}fps")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ScreenCapturerAndroid: ${e.message}", e)
        }
    }

    fun stopScreenCapture() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null
            videoSource?.dispose()
            videoSource = null
            localVideoTrack = null
            Log.i(TAG, "Screen capturer stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping screen capturer: ${e.message}")
        }
    }

    fun startAudioTrack() {
        val factory = peerConnectionFactory ?: return
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("AGENT_AUDIO_TRACK", audioSource)
        localAudioTrack?.setEnabled(true)
        Log.i(TAG, "Local audio track created and active.")
    }

    fun stopAudioTrack() {
        localAudioTrack?.setEnabled(false)
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
    }

    fun createPeerConnection(includeVideo: Boolean, includeAudio: Boolean) {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onLocalIceCandidateGenerated?.invoke(it) }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.CLOSED) {
                    onCallEnded?.invoke()
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        // Add Tracks
        if (includeAudio && localAudioTrack != null) {
            peerConnection?.addTrack(localAudioTrack, listOf("AGENT_MEDIA_STREAM"))
        }
        if (includeVideo && localVideoTrack != null) {
            peerConnection?.addTrack(localVideoTrack, listOf("AGENT_MEDIA_STREAM"))
        }
    }

    fun createOffer(onSuccess: (SessionDescription) -> Unit) {
        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(d: SessionDescription?) {}
                        override fun onSetSuccess() {
                            onSuccess(desc)
                            onLocalSdpGenerated?.invoke(desc)
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set local description: $err")
                        }
                    }, desc)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed to create offer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpMediaConstraints)
    }

    fun createAnswer(onSuccess: (SessionDescription) -> Unit) {
        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(d: SessionDescription?) {}
                        override fun onSetSuccess() {
                            onSuccess(desc)
                            onLocalSdpGenerated?.invoke(desc)
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set local description: $err")
                        }
                    }, desc)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed to create answer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpMediaConstraints)
    }

    fun setRemoteDescription(sessionDescription: SessionDescription, onSetSuccess: (() -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote description set successfully (${sessionDescription.type})")
                onSetSuccess?.invoke()
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote description: $err")
            }
        }, sessionDescription)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun close() {
        stopScreenCapture()
        stopAudioTrack()
        peerConnection?.close()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase.release()
    }

    companion object {
        private const val TAG = "WebRtcManager"
    }
}
