package com.example.soci_app.user_interface

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
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

    private var chatId: Int = 0
    private var userId: Int = 3 // Replace with actual user ID dynamically
    private var receiverId: Int = 0
    private val messages = mutableListOf<Message>()
    private var webSocket: WebSocket? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var currentUserId: Int = 0


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

        // Set up RecyclerView
        messageAdapter = MessageAdapter(messages, currentUserId)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = messageAdapter

        // Load previous messages
        loadMessages()

        // Connect WebSocket
        connectWebSocket()

        // Send new message
        sendButton.setOnClickListener {
            sendMessage()
        }

        // video call
        videoCallButton.setOnClickListener {
            val intent = Intent(this, VideoCallActivity::class.java)
            intent.putExtra("chat_id", chatId) // Pass chat ID
            intent.putExtra("receiver_id", receiverId) // Pass receiver ID
            startActivity(intent)
        }
    }

    private fun connectWebSocket() {
        val request = Request.Builder()
            .url("https://whole-crabs-train.loca.lt/${currentUserId}")
            .build()

        val client = OkHttpClient.Builder()
            .pingInterval(10, TimeUnit.SECONDS) // Keep connection alive
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
}
