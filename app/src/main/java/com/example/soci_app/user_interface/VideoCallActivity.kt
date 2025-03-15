package com.example.soci_app.user_interface

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.soci_app.R
import okhttp3.*
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
    private var userId: Int = 1  // Replace with actual logged-in user ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        // Get Chat & Receiver ID
        chatId = intent.getIntExtra("chat_id", 0)
        receiverId = intent.getIntExtra("receiver_id", 0)

        // Initialize UI Elements
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        startCallButton = findViewById(R.id.startCallButton)
        endCallButton = findViewById(R.id.endCallButton)

        // Initialize WebRTC
        initializeWebRTC()

        // Connect to WebSocket Signaling Server
        connectToSignalingServer()

        startCallButton.setOnClickListener { startCall() }
        endCallButton.setOnClickListener { endCall() }
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
            .url("https://two-spies-juggle.loca.lt")  // Replace with actual WebSocket URL
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

                // Handle signaling messages here
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebRTC", "WebSocket connection error: ${t.message}")
            }
        })
    }

    private fun startCall() {
        peerConnection = peerConnectionFactory.createPeerConnection(PeerConnection.RTCConfiguration(listOf()), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val candidateMessage = """{"type": "candidate", "target": "$receiverId", "candidate": "${candidate.sdp}"}"""
                webSocket?.send(candidateMessage)
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
                peerConnection?.setLocalDescription(this, sdp)
                val offerMessage = """{"type": "offer", "target": "$receiverId", "sdp": "${sdp.description}"}"""
                webSocket?.send(offerMessage)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(error: String?) {}

            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    private fun endCall() {
        peerConnection?.close()
        peerConnection = null
        finish()
    }
}
