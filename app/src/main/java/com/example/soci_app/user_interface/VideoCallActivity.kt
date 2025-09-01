package com.example.soci_app.user_interface

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
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
    private lateinit var endCallButton: Button
    private lateinit var callStatusText: TextView
    private lateinit var eglBase: EglBase
    private var videoCapturer: CameraVideoCapturer? = null
    private var webSocket: WebSocket? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private var chatId: Int = 0
    private var receiverId: Int = 0
    private var userId: Int = 0//Replace with actual logged-in user ID
    private var isCaller: Boolean = false

    enum class CallState {
        CONNECTING, CALLING, RINGING, CONNECTED, ENDED
    }
    private var callState: CallState = CallState.CONNECTING
    private var isWebRTCInitialized = false
    private var isSignalingConnected = false
    private var shouldStartCallWhenReady = false
    private var isEndingCall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        // Get Chat & Receiver ID
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        chatId = intent.getIntExtra("chat_id", 0)
        receiverId = intent.getIntExtra("receiver_id", 0)
        userId = sharedPreferences.getInt("USER_ID", 0)
        isCaller = intent.getBooleanExtra("is_caller", false)
        Log.e("WebRTC", "ULogged in " + userId)
        Log.e("WebRTC", "RLogged in " + receiverId)
        // Initialize UI Elements
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        endCallButton = findViewById(R.id.endCallButton)
        callStatusText = findViewById(R.id.callStatusText)

        checkPermissions()
        // Initialize EGL context for video rendering
        eglBase = EglBase.create()

        // Initialize WebRTC only if permissions are already granted
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (permissions.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            initializeWebRTC()
        }

        // Connect to WebSocket Signaling Server
        connectToSignalingServer()

        // Set initial call state and flags
        if (isCaller) {
            updateCallState(CallState.CALLING)
            shouldStartCallWhenReady = true
        } else {
            updateCallState(CallState.RINGING)
        }

        endCallButton.setOnClickListener { endCall() }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (permissions.any { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, 1001)
        } else {
            Log.d("WebRTC", "All permissions granted, initializing camera")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d("WebRTC", "Permissions granted, reinitializing WebRTC")
                // Permissions granted, initialize WebRTC again
                initializeWebRTC()
            } else {
                Log.e("WebRTC", "Camera/microphone permissions denied")
                android.widget.Toast.makeText(this, "Camera and microphone permissions are required for video calling", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeWebRTC() {
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
                localVideoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)

                videoCapturer!!.initialize(
                    SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
                    localVideoView.context,
                    videoSource.capturerObserver
                )

                localVideoView.init(eglBase.eglBaseContext, null)
                localVideoView.setMirror(true)
                localVideoView.setEnableHardwareScaler(true)

                // Initialize remote video view too
                remoteVideoView.init(eglBase.eglBaseContext, null)
                remoteVideoView.setMirror(false)
                remoteVideoView.setEnableHardwareScaler(true)
                Log.d("WebRTC", "✅ Remote video view initialized")

                localVideoTrack?.addSink(localVideoView)

                videoCapturer?.startCapture(1280, 720, 30)
                Log.d("WebRTC", "Camera capture started successfully")

                // Create audio track
                val audioConstraints = MediaConstraints()
                val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
                localAudioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)

                // Mark WebRTC as initialized and try to start call if needed
                isWebRTCInitialized = true
                Log.d("WebRTC", "✅ WebRTC initialization complete")
                tryStartCallIfReady()

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
            .url("ws://192.168.0.5:3001")  // Signaling server for WebRTC
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
                
                // Mark signaling as connected and try to start call if needed
                isSignalingConnected = true
                Log.d("WebRTC", "✅ Signaling connection established")
                tryStartCallIfReady()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebRTC", "Received WebSocket Message: $text")

                val message = JSONObject(text)
                when (message.getString("type")) {
                    "offer" -> handleOffer(message)
                    "answer" -> handleAnswer(message)
                    "candidate" -> handleCandidate(message)
                    "call_ended" -> {
                        Log.d("WebRTC", "Received call ended notification from remote peer")
                        runOnUiThread {
                            // The remote peer ended the call, clean up and exit
                            endCall(fromRemote = true)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebRTC", "WebSocket connection error vide: ${t.message}")
                runOnUiThread {
                    updateCallState(CallState.ENDED)
                    android.widget.Toast.makeText(this@VideoCallActivity, "Connection failed: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
                }
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
                
                // Add local stream BEFORE creating answer to generate ICE candidates  
                addLocalStreamToPeerConnection()

                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        Log.d("WebRTC", "Answer created: ${sdp.description}")

                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.d("WebRTC", "setLocalDescription SUCCESS for Answer")
                                Log.d("WebRTC", "🔥 Now expecting ICE candidates to be generated...")
                                val answerMessage = JSONObject().apply {
                                    put("type", "answer")
                                    put("target", message.getInt("initiatorId"))
                                    put("userId", userId) // Add sender ID
                                    put("sdp", sdp.description)
                                }.toString()
                                try {
                    webSocket?.send(answerMessage)
                } catch (e: Exception) {
                    Log.e("WebRTC", "Error sending answer: ${e.message}")
                }
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

    private fun getIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            // Multiple reliable STUN servers
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.nextcloud.com:443").createIceServer(),

            // TURN server for NAT traversal
            PeerConnection.IceServer.builder("turn:numb.viagenie.ca")
                .setUsername("webrtc@live.com")
                .setPassword("muazkh")
                .createIceServer()
        )
    }

    private fun createPeerConnection(): PeerConnection? {
        val iceServers = getIceServers()


        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        return peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d("WebRTC", "New ICE Candidate Generated: ${iceCandidate.sdp}")
                Log.d("WebRTC", "ICE Candidate sdpMid: ${iceCandidate.sdpMid}, sdpMLineIndex: ${iceCandidate.sdpMLineIndex}")

                // Send ICE candidate to the remote peer via WebSocket
                val iceMessage = JSONObject().apply {
                    put("type", "candidate")
                    put("target", receiverId)
                    put("userId", userId) // Add sender ID
                    put("candidate", iceCandidate.sdp)
                    put("sdpMid", iceCandidate.sdpMid)
                    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                }.toString()

                Log.d("WebRTC", "Sending ICE candidate to userId=$receiverId: $iceMessage")
                try {
                    webSocket?.send(iceMessage)
                    Log.d("WebRTC", "ICE candidate sent via WebSocket")
                } catch (e: Exception) {
                    Log.e("WebRTC", "Error sending ICE candidate: ${e.message}")
                }
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d("WebRTC", "📺 Remote stream received with ${stream.videoTracks.size} video tracks")
                runOnUiThread {
                    stream.videoTracks.firstOrNull()?.let { videoTrack ->
                        videoTrack.addSink(remoteVideoView)
                        updateCallState(CallState.CONNECTED)
                        Log.d("WebRTC", "✅ Remote video track added to view")
                    } ?: Log.w("WebRTC", "⚠️ No video track in remote stream")
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
                when (newState) {
                    PeerConnection.IceGatheringState.NEW -> Log.d("WebRTC", "ICE Gathering: NEW - Starting...")
                    PeerConnection.IceGatheringState.GATHERING -> Log.d("WebRTC", "ICE Gathering: GATHERING - Collecting candidates...")
                    PeerConnection.IceGatheringState.COMPLETE -> Log.d("WebRTC", "ICE Gathering: COMPLETE - All candidates collected")
                }
            }
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                Log.d("WebRTC", "🎬 onTrack called: ${transceiver?.receiver?.track()?.kind()}")
                transceiver?.receiver?.track()?.let { track ->
                    if (track.kind() == "video") {
                        runOnUiThread {
                            val videoTrack = track as VideoTrack
                            videoTrack.addSink(remoteVideoView)
                            updateCallState(CallState.CONNECTED)
                            Log.d("WebRTC", "✅ Remote video track connected via onTrack")
                        }
                    }
                }
            }
        })
    }

    private fun startCall() {
        val iceServers = getIceServers()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d("WebRTC", "Nated: ${iceCandidate.sdp}")
                Log.d("WebRTC", "ICE Candidate sdpMid: ${iceCandidate.sdpMid}, sdpMLineIndex: ${iceCandidate.sdpMLineIndex}")

                // Send ICE candidate to the remote peer via WebSocket
                val iceMessage = JSONObject().apply {
                    put("type", "candidate")
                    put("target", receiverId)
                    put("userId", userId) // Add sender ID
                    put("candidate", iceCandidate.sdp)
                    put("sdpMid", iceCandidate.sdpMid)
                    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                }.toString()

                Log.d("WebRTC", "Sending ICE candidate to userId=$receiverId: $iceMessage")
                try {
                    webSocket?.send(iceMessage)
                    Log.d("WebRTC", "ICE candidate sent via WebSocket")
                } catch (e: Exception) {
                    Log.e("WebRTC", "Error sending ICE candidate: ${e.message}")
                }
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

        // Add local stream BEFORE creating offer to generate ICE candidates
        addLocalStreamToPeerConnection()
        
        // Add small delay to ensure tracks are properly added
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("WebRTC", "Creating offer after adding tracks...")
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("WebRTC", "Offer created: ${sdp.description}")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTC", "setLocalDescription SUCCESS for Offer")
                        Log.d("WebRTC", "🔥 Now expecting ICE candidates to be generated...")
                        val offerMessage = JSONObject().apply {
                            put("type", "offer")
                            put("target", receiverId)
                            put("initiatorId", userId)
                            put("sdp", sdp.description)
                        }.toString()
                        try {
                            webSocket?.send(offerMessage)
                        } catch (e: Exception) {
                            Log.e("WebRTC", "Error sending offer: ${e.message}")
                        }
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
        }, 100) // 100ms delay
    }

    private fun addLocalStreamToPeerConnection() {
        try {
            Log.d("WebRTC", "=== ADDING TRACKS TO GENERATE ICE CANDIDATES ===")
            
            // Add audio track
            val audioConstraints = MediaConstraints()
            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            val audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)
            peerConnection?.addTrack(audioTrack, listOf("localStream"))
            Log.d("WebRTC", "✅ Audio track added")
            
            // Add video track if available
            localVideoTrack?.let { videoTrack ->
                peerConnection?.addTrack(videoTrack, listOf("localStream"))
                Log.d("WebRTC", "✅ Video track added")
            } ?: Log.w("WebRTC", "⚠️ Video track not available")
            
        } catch (e: Exception) {
            Log.e("WebRTC", "❌ Error adding tracks: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun endCall(fromRemote: Boolean = false) {
        // Prevent multiple calls to endCall
        if (isEndingCall) {
            Log.d("WebRTC", "Already ending call, skipping duplicate endCall")
            return
        }
        isEndingCall = true
        
        updateCallState(CallState.ENDED)
        
        // Only notify signaling server if this is initiated locally (not from remote end message)
        if (!fromRemote) {
            val endCallMessage = JSONObject().apply {
                put("type", "call_ended")
                put("userId", userId)
                put("target", receiverId)
            }
            try {
                webSocket?.send(endCallMessage.toString())
                Log.d("WebRTC", "Call end notification sent to server")
            } catch (e: Exception) {
                Log.e("WebRTC", "Error sending call end notification: ${e.message}")
            }
        }
        
        // Clean up resources properly
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            
            localVideoTrack?.dispose()
            localVideoTrack = null
            
            localAudioTrack?.dispose()
            localAudioTrack = null
            
            peerConnection?.close()
            peerConnection = null
            
            // Close WebSocket connection
            webSocket?.close(1000, "Call ended")
            webSocket = null
            
            // Clean up video views
            localVideoView.release()
            remoteVideoView.release()
            eglBase.release()
            
            Log.d("WebRTC", "All resources cleaned up successfully")
        } catch (e: Exception) {
            Log.e("WebRTC", "Error during cleanup: ${e.message}")
        }
        
        finish()
    }

    private fun updateCallState(newState: CallState) {
        callState = newState
        runOnUiThread {
            val statusText = when (newState) {
                CallState.CONNECTING -> "Connecting..."
                CallState.CALLING -> "Calling..."
                CallState.RINGING -> "Incoming call..."
                CallState.CONNECTED -> {
                    // Hide status text when connected
                    callStatusText.visibility = android.view.View.GONE
                    return@runOnUiThread
                }
                CallState.ENDED -> "Call ended"
            }
            callStatusText.text = statusText
            callStatusText.visibility = android.view.View.VISIBLE
        }
    }

    private fun tryStartCallIfReady() {
        Log.d("WebRTC", "Checking if ready to start call...")
        Log.d("WebRTC", "WebRTC initialized: $isWebRTCInitialized")
        Log.d("WebRTC", "Signaling connected: $isSignalingConnected") 
        Log.d("WebRTC", "Should start call: $shouldStartCallWhenReady")
        
        if (isWebRTCInitialized && isSignalingConnected && shouldStartCallWhenReady) {
            Log.d("WebRTC", "🚀 All conditions met - starting call!")
            shouldStartCallWhenReady = false // Prevent multiple calls
            
            // Small delay to ensure everything is properly initialized
            Handler(Looper.getMainLooper()).postDelayed({
                startCall()
            }, 500)
        }
    }
}
