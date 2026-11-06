package com.orra.app

import android.content.Context
import android.view.View
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

class OrraWebRtcEngine(
    private val context: Context,
    private val signaling: OrraCallSignaling,
    private val callId: String,
    private val caller: Boolean,
    private val videoEnabled: Boolean,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val egl = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var lastSignalId = 0L
    private var remoteDescriptionReady = false
    private val pendingIce = mutableListOf<IceCandidate>()
    private val signalExecutor = Executors.newSingleThreadExecutor()
    var muted = false
        private set
    var cameraEnabled = true
        private set

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local
        remoteRenderer = remote
        local?.let { runCatching { it.init(egl.eglBaseContext, null); it.setMirror(true) } }
        remote?.let { runCatching { it.init(egl.eglBaseContext, null); it.setMirror(false) } }
        videoTrack?.let { track -> local?.let(track::addSink) }
    }

    fun start(configuredIceServers: List<PeerConnection.IceServer> = emptyList()) {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
            )
            val audioModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .createPeerConnectionFactory()
            val iceServers = configuredIceServers.ifEmpty {
                listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            }
            peer = factory?.createPeerConnection(PeerConnection.RTCConfiguration(iceServers), observer)
                ?: error("Could not create WebRTC peer connection.")
            val constraints = MediaConstraints()
            audioSource = factory?.createAudioSource(constraints)
            audioTrack = factory?.createAudioTrack("orra-audio-$callId", audioSource)
            audioTrack?.let { peer?.addTrack(it, listOf("orra-stream")) }
            if (videoEnabled) createCameraTrack()
            if (caller) createOffer()
            onStatus(if (caller) "Calling…" else "Connecting…")
        } catch (e: Exception) {
            onError(e.message ?: "Could not start the internet call.")
        }
    }

    private fun createCameraTrack() {
        val f = factory ?: return
        val enumerator: CameraEnumerator = Camera2Enumerator(context)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: throw IllegalStateException("No camera is available.")
        capturer = enumerator.createCapturer(name, null) as? CameraVideoCapturer
            ?: throw IllegalStateException("Could not open the camera.")
        val source = f.createVideoSource(false)
        videoSource = source
        textureHelper = SurfaceTextureHelper.create("ORRA-Camera", egl.eglBaseContext)
        capturer?.initialize(textureHelper, context, source.capturerObserver)
        capturer?.startCapture(720, 1280, 24)
        videoTrack = f.createVideoTrack("orra-video-$callId", source)
        videoTrack?.setEnabled(true)
        videoTrack?.let { track ->
            localRenderer?.let(track::addSink)
            peer?.addTrack(track, listOf("orra-stream"))
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> onStatus("Connected")
                PeerConnection.IceConnectionState.DISCONNECTED -> onStatus("Connection interrupted…")
                PeerConnection.IceConnectionState.FAILED -> onError("WebRTC could not establish a media route. A TURN server may be required on this network.")
                else -> Unit
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            signalExecutor.execute {
                runCatching {
                    signaling.sendSignal(callId, "ice", org.json.JSONObject()
                        .put("sdp", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex))
                }.onFailure { onError(it.message ?: "Could not send network candidate.") }
            }
        }
        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.firstOrNull()?.let { track -> remoteRenderer?.let(track::addSink) }
        }
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { track -> remoteRenderer?.let(track::addSink) }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { track -> remoteRenderer?.let(track::addSink) }
        }
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {
            peer?.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    val kind = if (description.type == SessionDescription.Type.OFFER) "offer" else "answer"
                    signalExecutor.execute {
                        runCatching {
                            signaling.sendSignal(callId, kind, org.json.JSONObject()
                                .put("type", description.type.canonicalForm())
                                .put("sdp", description.description))
                        }.onFailure { onError(it.message ?: "Could not send call setup data.") }
                    }
                }
                override fun onCreateFailure(error: String) { onError(error) }
                override fun onSetFailure(error: String) { onError(error) }
            }, description)
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String) { onError(error) }
        override fun onSetFailure(error: String) { onError(error) }
    }

    private fun createOffer() {
        peer?.createOffer(sdpObserver, MediaConstraints())
    }

    fun handleSignal(signal: OrraCallSignal) {
        try {
            when (signal.type) {
                "offer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, signal.payload.optString("sdp"))
                    peer?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            remoteDescriptionReady = true
                            flushIce()
                            peer?.createAnswer(sdpObserver, MediaConstraints())
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(error: String) { onError(error) }
                        override fun onSetFailure(error: String) { onError(error) }
                    }, sdp)
                }
                "answer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, signal.payload.optString("sdp"))
                    peer?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() { remoteDescriptionReady = true; flushIce() }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(error: String) { onError(error) }
                        override fun onSetFailure(error: String) { onError(error) }
                    }, sdp)
                }
                "ice" -> {
                    val candidate = IceCandidate(
                        signal.payload.optString("sdpMid"),
                        signal.payload.optInt("sdpMLineIndex"),
                        signal.payload.optString("sdp")
                    )
                    if (remoteDescriptionReady) peer?.addIceCandidate(candidate) else pendingIce.add(candidate)
                }
                "hangup" -> onStatus("The other person ended the call")
            }
        } catch (e: Exception) { onError(e.message ?: "Invalid call signal.") }
    }

    private fun flushIce() {
        pendingIce.forEach { peer?.addIceCandidate(it) }
        pendingIce.clear()
    }

    fun setMuted(value: Boolean) {
        muted = value
        audioTrack?.setEnabled(!value)
    }

    fun setCameraEnabled(value: Boolean) {
        cameraEnabled = value
        videoTrack?.setEnabled(value)
        if (value) runCatching { capturer?.startCapture(720, 1280, 24) }
            .onFailure { onError("Could not enable camera.") }
        else runCatching { capturer?.stopCapture() }
    }

    fun release() {
        runCatching { capturer?.stopCapture() }
        runCatching { videoTrack?.let { track -> localRenderer?.let(track::removeSink) } }
        runCatching { videoTrack?.let { track -> remoteRenderer?.let(track::removeSink) } }
        runCatching { remoteRenderer?.release() }
        runCatching { localRenderer?.release() }
        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { textureHelper?.dispose() }
        runCatching { factory?.dispose() }
        signalExecutor.shutdownNow()
        runCatching { egl.release() }
    }
}
