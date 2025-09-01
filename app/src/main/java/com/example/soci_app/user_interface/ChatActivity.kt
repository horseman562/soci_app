package com.example.soci_app.user_interface

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R
import com.example.soci_app.adapter.MessageAdapter
import com.example.soci_app.model.Message
import com.example.soci_app.api.RetrofitClient
import okhttp3.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class ChatActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var videoCallButton: ImageButton
    private lateinit var backButton: ImageButton

    private var chatId: Int = 0
    private var userId: Int = 3 // Replace with actual user ID dynamically
    private var receiverId: Int = 0
    private val messages = mutableListOf<Message>()
    private var webSocket: WebSocket? = null
    private var signalingWebSocket: WebSocket? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var currentUserId: Int = 0
    private var incomingCallDialog: AlertDialog? = null
    private var callTimeoutHandler: Handler? = null
    private var callTimeoutRunnable: Runnable? = null
    private var isSignalingConnected = false
    private var lastCallEndTime = 0L
    private val CALL_COOLDOWN_MS = 3000L // 3 second cooldown between calls


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        currentUserId = sharedPreferences.getInt("USER_ID", 0)

        // Get chatId from intent
        chatId = intent.getIntExtra("chat_id", 0)
        receiverId = intent.getIntExtra("receiver_id", 0)

        // Initialize UI components
        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        videoCallButton = findViewById(R.id.videoCallButton)
        backButton = findViewById(R.id.backButton)

        // Set up RecyclerView
        messageAdapter = MessageAdapter(messages, currentUserId)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = messageAdapter

        // Load previous messages
        loadMessages()

        // Connect WebSocket
        connectWebSocket()
        
        // Connect to Signaling Server for video calls
        connectSignalingServer()

        // Send new message
        sendButton.setOnClickListener {
            sendMessage()
        }

        // video call
        videoCallButton.setOnClickListener {
            initiateVideoCall()
        }

        // back button
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun connectWebSocket() {
        val request = Request.Builder()
            .url("ws://192.168.0.5:1122/${currentUserId}")
            .build()

        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout
            .writeTimeout(0, TimeUnit.MILLISECONDS) // No write timeout
            .connectTimeout(30, TimeUnit.SECONDS) // Connection timeout
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("WebSocket", "Connected to WebSocket server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                Log.d("WebSocket Message", "Connected to WebSocket message ${json}")
                
                if (json.getString("type") == "message") {
                    val message = Message(
                        id = 0,
                        chat_id = chatId,
                        sender_id = json.getInt("sender_id"),
                        message = json.getString("message"),
                        created_at = "Now"
                    )

                    runOnUiThread {
                        messageAdapter.addMessage(message)
                        recyclerView.scrollToPosition(messageAdapter.itemCount - 1)
                        messageInput.text.clear()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("WebSocket", "Connection Error: ${t.message}")
                reconnectWebSocket()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "WebSocket closed: $reason")
                reconnectWebSocket()
            }
        })
    }

    private fun reconnectWebSocket() {
        Log.d("WebSocket", "Reconnecting in 3 seconds...")
        android.os.Handler(mainLooper).postDelayed({ connectWebSocket() }, 3000)
    }

    private fun reconnectSignalingServer() {
        Log.d("SignalingWS", "Reconnecting to signaling server in 3 seconds...")
        android.os.Handler(mainLooper).postDelayed({ connectSignalingServer() }, 3000)
    }

    private fun connectSignalingServer() {
        Log.d("SignalingWS", "Attempting to connect to signaling server...")
        
        val request = Request.Builder()
            .url("ws://192.168.0.5:3001")
            .build()

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        signalingWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("SignalingWS", "Connected to Signaling Server")
                isSignalingConnected = true
                
                // Register user
                val registerMessage = JSONObject().apply {
                    put("type", "register")
                    put("userId", currentUserId.toString())
                }
                webSocket.send(registerMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                Log.d("SignalingWS", "Received: $json")
                
                when (json.getString("type")) {
                    "call_request" -> {
                        runOnUiThread {
                            showIncomingCallDialog(json.getInt("caller_id"))
                        }
                    }
                    "call_accepted" -> {
                        runOnUiThread {
                            cancelCallTimeout()
                            startVideoCall(true) // Caller
                        }
                    }
                    "call_declined" -> {
                        runOnUiThread {
                            cancelCallTimeout()
                            Toast.makeText(this@ChatActivity, "Call was declined", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "call_ended" -> {
                        runOnUiThread {
                            // Reset any call-related UI state if needed
                            cancelCallTimeout()
                            incomingCallDialog?.dismiss()
                            lastCallEndTime = System.currentTimeMillis()
                            Log.d("SignalingWS", "Call ended notification received from user ${json.optInt("userId", -1)}")
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("SignalingWS", "Connection Error: ${t.message}")
                isSignalingConnected = false
                // Reconnect signaling server after failure
                reconnectSignalingServer()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SignalingWS", "Signaling WebSocket closed: $reason")
                isSignalingConnected = false
                // Only reconnect if it wasn't a normal closure
                if (code != 1000) {
                    reconnectSignalingServer()
                }
            }
        })
    }

    private fun loadMessages() {
        Log.e("Load MEssage", "Load MEssage ${chatId}")
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("AUTH_TOKEN", null) ?: ""

        val authHeader = "Bearer $token"
        Log.e("Load Token", "Load Token ${token}")
        RetrofitClient.instance.getMessages(authHeader, chatId).enqueue(object : Callback<List<Message>> {
            override fun onResponse(call: Call<List<Message>>, response: Response<List<Message>>) {

                if (response.isSuccessful) {
                    messages.clear()
                    response.body()?.let {
                        messages.addAll(it)
                        messageAdapter.notifyDataSetChanged()
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                } else {
                    Log.e("ChatActivity", "Failed to load messages: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<Message>>, t: Throwable) {
                Log.e("ChatActivity", "Error fetching messages: ${t.message}")
            }
        })
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getInt("USER_ID", -1) ?: ""

        if (messageText.isEmpty()) return

        val jsonMessage = JSONObject().apply {
            put("chat_id", chatId)
            put("type", "message")
            put("message", messageText)
            put("sender_id", userId)
            //put("receiver_id", receiverId) // Change dynamically
        }

//        val message = Message(
//            id = 0,
//            chat_id = chatId,
//            sender_id = json.getInt("sender_id"),
//            message = json.getString("message"),
//            created_at = "Now"
//        )

        webSocket?.send(jsonMessage.toString())


    }

    private fun initiateVideoCall() {
        Log.d("SignalingWS", "Attempting to initiate video call. Connection status: $isSignalingConnected")
        
        // Check cooldown period
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCallEndTime < CALL_COOLDOWN_MS) {
            val remaining = (CALL_COOLDOWN_MS - (currentTime - lastCallEndTime)) / 1000
            Toast.makeText(this, "Please wait ${remaining}s before calling again", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isSignalingConnected || signalingWebSocket == null) {
            Log.e("SignalingWS", "Cannot initiate call - signaling not connected!")
            Toast.makeText(this, "Connection not ready, please try again", Toast.LENGTH_SHORT).show()
            return
        }
        
        val callRequestMessage = JSONObject().apply {
            put("type", "call_request")
            put("caller_id", currentUserId)
            put("receiver_id", receiverId)
        }
        
        Log.d("SignalingWS", "Sending call request: $callRequestMessage")
        try {
            signalingWebSocket?.send(callRequestMessage.toString())
            Toast.makeText(this, "Calling...", Toast.LENGTH_SHORT).show()
            
            // Set call timeout (30 seconds)
            startCallTimeout()
        } catch (e: Exception) {
            Log.e("SignalingWS", "Error sending call request: ${e.message}")
            Toast.makeText(this, "Failed to initiate call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCallTimeout() {
        callTimeoutHandler = Handler(mainLooper)
        callTimeoutRunnable = Runnable {
            Toast.makeText(this, "Call timed out - no response", Toast.LENGTH_LONG).show()
        }
        callTimeoutHandler?.postDelayed(callTimeoutRunnable!!, 30000) // 30 seconds
    }

    private fun cancelCallTimeout() {
        callTimeoutRunnable?.let { runnable ->
            callTimeoutHandler?.removeCallbacks(runnable)
        }
        callTimeoutHandler = null
        callTimeoutRunnable = null
    }

    private fun showIncomingCallDialog(callerId: Int) {
        if (incomingCallDialog?.isShowing == true) return // Prevent multiple dialogs
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_incoming_call, null)
        val callerNameText = dialogView.findViewById<TextView>(R.id.callerNameText)
        val acceptButton = dialogView.findViewById<Button>(R.id.acceptCallButton)
        val declineButton = dialogView.findViewById<Button>(R.id.declineCallButton)
        
        callerNameText.text = "User $callerId" // You can replace this with actual name lookup
        
        incomingCallDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        acceptButton.setOnClickListener {
            acceptCall(callerId)
            incomingCallDialog?.dismiss()
        }
        
        declineButton.setOnClickListener {
            declineCall(callerId)
            incomingCallDialog?.dismiss()
        }
        
        incomingCallDialog?.show()
    }

    private fun acceptCall(callerId: Int) {
        val acceptMessage = JSONObject().apply {
            put("type", "call_accepted")
            put("caller_id", callerId)
            put("receiver_id", currentUserId)
        }
        
        signalingWebSocket?.send(acceptMessage.toString())
        startVideoCall(false) // Receiver
    }

    private fun declineCall(callerId: Int) {
        val declineMessage = JSONObject().apply {
            put("type", "call_declined")
            put("caller_id", callerId)
            put("receiver_id", currentUserId)
        }
        
        signalingWebSocket?.send(declineMessage.toString())
    }

    private fun startVideoCall(isCaller: Boolean) {
        val intent = Intent(this, VideoCallActivity::class.java)
        intent.putExtra("chat_id", chatId)
        intent.putExtra("receiver_id", receiverId)
        intent.putExtra("is_caller", isCaller)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Always reconnect signaling server when resuming to ensure fresh connection
        Log.d("SignalingWS", "Forcing fresh signaling connection on resume")
        isSignalingConnected = false
        lastCallEndTime = System.currentTimeMillis() // Set cooldown when returning from video call
        signalingWebSocket?.close(1000, "Forcing fresh connection")
        connectSignalingServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        incomingCallDialog?.dismiss()
        cancelCallTimeout()
        
        // Clean up WebSocket connections
        isSignalingConnected = false
        webSocket?.close(1000, "Activity destroyed")
        signalingWebSocket?.close(1000, "Activity destroyed")
    }
}
