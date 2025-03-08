package com.example.soci_app.user_interface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R
import com.example.soci_app.adapters.ChatListAdapter
import com.example.soci_app.api.RetrofitClient
import com.example.soci_app.model.Chat
//import com.example.soci_app.model.Chat

import com.google.android.material.bottomnavigation.BottomNavigationView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {

    private lateinit var welcomeText: TextView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // UI Elements
        welcomeText = findViewById(R.id.welcomeText)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Setup RecyclerView
        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        // Get Auth Token
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("AUTH_TOKEN", null)

        Log.d("TOKEN_CHECK", "Retrieved token: $token")

        if (!token.isNullOrEmpty()) {
            loadRecentChats(token)
        } else {
            Toast.makeText(this, "Authentication required. Redirecting...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Handle Bottom Navigation Clicks
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_chat -> Toast.makeText(this, "Already in Chat", Toast.LENGTH_SHORT).show()
//                R.id.menu_video -> startActivity(Intent(this, VideoCallActivity::class.java))
//                R.id.menu_notifications -> startActivity(Intent(this, NotificationActivity::class.java))
            }
            true
        }
    }

    private fun loadRecentChats(token: String) {
        val authHeader = "Bearer ${token.trim()}" // Ensure token is clean
        Log.d("AUTH_HEADER", "Token Sent: $authHeader") // Log token before request

        RetrofitClient.instance.getRecentChats(authHeader).enqueue(object : Callback<List<Chat>> {
            override fun onResponse(call: Call<List<Chat>>, response: Response<List<Chat>>) {
                Log.d("API_RESPONSE", "Status Code: ${response.code()}")

                if (response.isSuccessful) {
                    val chats = response.body()
                    Log.d("API_RESPONSE", "Chats: $chats")

                    if (!chats.isNullOrEmpty()) {
                        // Get the logged-in user ID from SharedPreferences
                        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
                        val currentUserId = sharedPreferences.getInt("USER_ID", 0) // Default to 0 if not found

                        // Pass currentUserId to ChatListAdapter
                        chatRecyclerView.adapter = ChatListAdapter(chats, currentUserId) { chat ->
                            val intent = Intent(this@HomeActivity, ChatActivity::class.java)
                            intent.putExtra("chat_id", chat.id)
                            startActivity(intent)
                        }

                        // 🔥 Force UI update
                        runOnUiThread {
                            chatRecyclerView.adapter?.notifyDataSetChanged()
                        }
                    } else {
                        Toast.makeText(this@HomeActivity, "No recent chats available", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API_ERROR", "Error Body: $errorBody")
                    Toast.makeText(this@HomeActivity, "Failed to load chats: $errorBody", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<List<Chat>>, t: Throwable) {
                Log.e("NETWORK_ERROR", "Error: ${t.message}")
                Toast.makeText(this@HomeActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


}
