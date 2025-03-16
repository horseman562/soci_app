package com.example.soci_app.user_interface

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.soci_app.R
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocketFactory
import java.util.concurrent.TimeUnit

class VideoCallActivity : AppCompatActivity() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var startCallButton: Button
    private lateinit var endCallButton: Button
    private lateinit var eglBase: EglBase
    private var videoCapturer: CameraVideoCapturer? = null
    private var webSocket: WebSocket? = null

    private var chatId: Int = 0
    private var receiverId: Int = 0
    private var userId: Int = 0//Replace with actual logged-in user ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        // Get Chat & Receiver ID
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        chatId = intent.getIntExtra("chat_id", 0)
        receiverId = intent.getIntExtra("receiver_id", 0)
        userId = sharedPreferences.getInt("USER_ID", 0)
        Log.e("WebRTC", "ULogged in " + userId)
        Log.e("WebRTC", "RLogged in " + receiverId)
        // Initialize UI Elements
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        startCallButton = findViewById(R.id.startCallButton)
        endCallButton = findViewById(R.id.endCallButton)

        //checkPermissions()

        // Initialize WebRTC
        initializeWebRTC()

        // Connect to WebSocket Signaling Server
        connectToSignalingServer()

        startCallButton.setOnClickListener { startCall() }
        endCallButton.setOnClickListener { endCall() }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (permissions.any { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, 1001)
        }
    }

    private fun initializeWebRTC() {
        // Initialize EGL context for video rendering
        eglBase = EglBase.create()

        // Initialize WebRTC
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Create PeerConnectionFactory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        // Ensure Video Capturer is Initialized Before Starting Capture
        videoCapturer = createVideoCapturer()
        if (videoCapturer == null) {
            Log.e("WebRTC", "Failed to initialize CameraVideoCapturer")
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                val videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)

                videoCapturer!!.initialize(
                    SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
                    localVideoView.context,
                    videoSource.capturerObserver
                )

                localVideoView.init(eglBase.eglBaseContext, null)
                localVideoView.setMirror(true)
                localVideoView.setEnableHardwareScaler(true)

                videoTrack.addSink(localVideoView)

                videoCapturer?.startCapture(1280, 720, 30)
                Log.d("WebRTC", "Camera capture started successfully")

                val localStream = peerConnectionFactory.createLocalMediaStream("localStream")
                localStream.addTrack(videoTrack)

            } catch (e: Exception) {
                Log.e("WebRTC", "Error starting video capture: ${e.message}")
            }
        }, 500)
    }

    private fun createVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(this)

        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, object : CameraVideoCapturer.CameraEventsHandler {
                    override fun onCameraOpening(cameraName: String?) {
                        Log.d("WebRTC", "Camera opening: $cameraName")
                    }

                    override fun onCameraError(errorDescription: String?) {
                        Log.e("WebRTC", "Camera error: $errorDescription")
                    }

                    override fun onCameraClosed() {
                        Log.d("WebRTC", "Camera closed")
                    }

                    override fun onCameraDisconnected() {
                        Log.d("WebRTC", "Camera disconnected")
                    }

                    override fun onCameraFreezed(errorDescription: String?) {
                        Log.e("WebRTC", "Camera freeze: $errorDescription")
                    }

                    override fun onFirstFrameAvailable() {
                        Log.d("WebRTC", "First frame available")
                    }
                })
            }
        }
        return null
    }

    private fun connectToSignalingServer() {
        val request = Request.Builder()
            .url("https://salty-results-visit.loca.lt")  // Replace with actual WebSocket URL
            .build()

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSockets require infinite read timeout
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebRTC", "Connected to WebSocket Signaling Server")

                // Send registration message
                val registerMessage = """{"type": "register", "userId": "$userId"}"""
                webSocket.send(registerMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebRTC", "Received WebSocket Message: $text")

                val message = JSONObject(text)
                when (message.getString("type")) {
                    "offer" -> handleOffer(message)
                    "answer" -> handleAnswer(message)
                    "candidate" -> handleCandidate(message)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebRTC", "WebSocket connection error vide: ${t.message}")
            }
        })
    }

    private fun handleOffer(message: JSONObject) {
        Log.d("WebRTC", "Received Offer SDP: ${message.getString("sdp")}")
        peerConnection = createPeerConnection()

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, message.getString("sdp"))
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTC", "setRemoteDescription SUCCESS for Offer")

                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        Log.d("WebRTC", "Answer created: ${sdp.description}")

                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.d("WebRTC", "setLocalDescription SUCCESS for Answer")
                                val answerMessage = JSONObject().apply {
                                    put("type", "answer")
                                    put("target", message.getInt("initiatorId"))
                                    put("sdp", sdp.description)
                                }.toString()
                                webSocket?.send(answerMessage)
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e("WebRTC", "setLocalDescription FAILED for Answer: $error")
                            }

                            override fun onCreateSuccess(sdp: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                        }, sdp)
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e("WebRTC", "createAnswer FAILED: $error")
                    }

                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String?) {}
                }, MediaConstraints())
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTC", "setRemoteDescription FAILED: $error")
            }

            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, remoteSdp)
    }


    private fun handleAnswer(message: JSONObject) {
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, message.getString("sdp"))
        Log.d("WebRTC", "Received Answer SDP: ${message.getString("sdp")}")

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTC", "setRemoteDescription SUCCESS for Answer")
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTC", "setRemoteDescription FAILED for Answer: $error")
            }

            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, remoteSdp)
    }


    private fun handleCandidate(message: JSONObject) {
        val iceCandidate = IceCandidate(
            message.getString("sdpMid"),
            message.getInt("sdpMLineIndex"),
            message.getString("candidate")
        )

        Log.d("WebRTC", "Adding ICE Candidate: $iceCandidate")
        peerConnection?.addIceCandidate(iceCandidate)
    }


    private fun createPeerConnection(): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        return peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d("WebRTC", "New ICE Candidate: ${iceCandidate.sdp}")

                // Send ICE candidate to the remote peer via WebSocket
                val iceMessage = JSONObject().apply {
                    put("type", "candidate")
                    put("target", receiverId)
                    put("candidate", iceCandidate.sdp)
                    put("sdpMid", iceCandidate.sdpMid)
                    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                }.toString()

                webSocket?.send(iceMessage)
            }

            override fun onAddStream(stream: MediaStream) {
                runOnUiThread {
                    stream.videoTracks.firstOrNull()?.addSink(remoteVideoView)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d("WebRTC", "ICE Connection State changed: $newState")
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onDataChannel(dataChannel: DataChannel) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                Log.d("WebRTC", "ICE Gathering State changed: $newState")
            }
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        })
    }

    private fun startCall() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d("WebRTC", "New ICE Candidate: ${iceCandidate.sdp}")

                // Send ICE candidate to the remote peer via WebSocket
                val iceMessage = JSONObject().apply {
                    put("type", "candidate")
                    put("target", receiverId)
                    put("candidate", iceCandidate.sdp)
                    put("sdpMid", iceCandidate.sdpMid)
                    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                }.toString()

                webSocket?.send(iceMessage)
            }

            override fun onAddStream(stream: MediaStream) {
                runOnUiThread {
                    stream.videoTracks.firstOrNull()?.addSink(remoteVideoView)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}

            override fun onIceConnectionReceivingChange(p0: Boolean) {}

            override fun onDataChannel(dataChannel: DataChannel) {}

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}

            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onRenegotiationNeeded() {}

            override fun onTrack(transceiver: RtpTransceiver?) {}
        })

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("WebRTC", "Offer created: ${sdp.description}")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTC", "setLocalDescription SUCCESS for Offer")
                        val offerMessage = JSONObject().apply {
                            put("type", "offer")
                            put("target", receiverId)
                            put("initiatorId", userId)
                            put("sdp", sdp.description)
                        }.toString()
                        webSocket?.send(offerMessage)
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTC", "setLocalDescription FAILED for Offer: $error")
                    }

                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTC", "createOffer FAILED: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    private fun endCall() {
        peerConnection?.close()
        peerConnection = null
        finish()
    }
}
