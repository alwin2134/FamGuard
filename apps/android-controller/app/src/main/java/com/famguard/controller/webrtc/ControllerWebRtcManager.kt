package com.famguard.controller.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Controller WebRTC Manager for rendering child's screen and conducting voice calls.
 */
class ControllerWebRtcManager(private val context: Context) {

    val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    // Audio
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Video Renderer
    private var remoteVideoTrack: VideoTrack? = null
    private var attachedRenderer: SurfaceViewRenderer? = null

    var onLocalSdpGenerated: ((SessionDescription) -> Unit)? = null
    var onLocalIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onRemoteVideoTrackReceived: ((VideoTrack) -> Unit)? = null
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
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun startAudioTrack() {
        val factory = peerConnectionFactory ?: return
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("CONTROLLER_AUDIO_TRACK", audioSource)
        localAudioTrack?.setEnabled(true)

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        } catch (_: Exception) {}
    }

    fun createPeerConnection(includeAudio: Boolean = true) {
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
                Log.d(TAG, "Controller ICE Connection State: $state")
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

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    Log.i(TAG, "Received remote VideoTrack from child device!")
                    remoteVideoTrack = track
                    attachedRenderer?.let { track.addSink(it) }
                    onRemoteVideoTrackReceived?.invoke(track)
                }
            }
        })

        if (includeAudio && localAudioTrack != null) {
            peerConnection?.addTrack(localAudioTrack, listOf("CONTROLLER_MEDIA_STREAM"))
        }
    }

    fun attachVideoRenderer(renderer: SurfaceViewRenderer) {
        attachedRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        renderer.setEnableHardwareScaler(true)
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachVideoRenderer() {
        attachedRenderer?.let {
            remoteVideoTrack?.removeSink(it)
            it.release()
        }
        attachedRenderer = null
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
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
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
                Log.i(TAG, "Controller: Remote description set successfully (${sessionDescription.type})")
                onSetSuccess?.invoke()
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Controller: Failed to set remote description: $err")
            }
        }, sessionDescription)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun setMicrophoneMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        audioManager.isSpeakerphoneOn = speakerOn
    }

    fun close() {
        detachVideoRenderer()
        localAudioTrack?.setEnabled(false)
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnection?.close()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        eglBase.release()
    }

    companion object {
        private const val TAG = "CtrlWebRtcManager"
    }
}
