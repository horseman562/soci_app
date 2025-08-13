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
import com.example.soci_app.adapter.PhoneNumberAdapter
import com.example.soci_app.api.RetrofitClient
import com.example.soci_app.api.CreateChatRequest
import com.example.soci_app.api.UserCheckResponse
import com.example.soci_app.model.Chat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ContactDetailActivity : AppCompatActivity() {

    private lateinit var contactNameTextView: TextView
    private lateinit var phoneNumbersRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_detail)

        contactNameTextView = findViewById(R.id.contactNameTextView)
        phoneNumbersRecyclerView = findViewById(R.id.phoneNumbersRecyclerView)

        phoneNumbersRecyclerView.layoutManager = LinearLayoutManager(this)

        val contactName = intent.getStringExtra("contact_name") ?: "Unknown"
        val phoneNumbers = intent.getStringArrayListExtra("contact_phones") ?: arrayListOf()

        contactNameTextView.text = contactName

        phoneNumbersRecyclerView.adapter = PhoneNumberAdapter(phoneNumbers) { phoneNumber ->
            checkUserAndStartChat(phoneNumber)
        }
    }

    private fun checkUserAndStartChat(phoneNumber: String) {
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("AUTH_TOKEN", null)
        val currentUserId = sharedPreferences.getInt("USER_ID", 0)

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            return
        }

        // Normalize phone number for comparison
        val normalizedPhone = phoneNumber.replace("\\s".toRegex(), "").replace("-", "")
        val authHeader = "Bearer ${token.trim()}"

        Log.d("USER_CHECK", "Checking phone: $normalizedPhone")

        RetrofitClient.instance.checkUserByPhone(authHeader, normalizedPhone).enqueue(object : Callback<UserCheckResponse> {
            override fun onResponse(call: Call<UserCheckResponse>, response: Response<UserCheckResponse>) {
                if (response.isSuccessful) {
                    val userCheckResponse = response.body()
                    if (userCheckResponse?.exists == true && userCheckResponse.user != null) {
                        val foundUser = userCheckResponse.user
                        Log.d("USER_CHECK", "User found: ${foundUser.name}")
                        
                        // Create or get existing chat
                        createOrGetChat(currentUserId, foundUser.id)
                    } else {
                        Toast.makeText(this@ContactDetailActivity, 
                            "User with this phone number is not registered in the app", 
                            Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("USER_CHECK", "Error: ${response.errorBody()?.string()}")
                    Toast.makeText(this@ContactDetailActivity, 
                        "Failed to check user registration", 
                        Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UserCheckResponse>, t: Throwable) {
                Log.e("USER_CHECK", "Network error: ${t.message}")
                Toast.makeText(this@ContactDetailActivity, 
                    "Network error: ${t.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createOrGetChat(currentUserId: Int, targetUserId: Int) {
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("AUTH_TOKEN", null)
        val authHeader = "Bearer ${token?.trim()}"

        val chatRequest = CreateChatRequest(currentUserId, targetUserId)

        RetrofitClient.instance.createChat(authHeader, chatRequest).enqueue(object : Callback<Chat> {
            override fun onResponse(call: Call<Chat>, response: Response<Chat>) {
                if (response.isSuccessful) {
                    val chat = response.body()
                    if (chat != null) {
                        Log.d("CHAT_CREATE", "Chat created/found: ${chat.id}")
                        
                        // Navigate to ChatActivity
                        val intent = Intent(this@ContactDetailActivity, ChatActivity::class.java)
                        intent.putExtra("chat_id", chat.id)
                        intent.putExtra("receiver_id", targetUserId)
                        startActivity(intent)
                    }
                } else {
                    Log.e("CHAT_CREATE", "Error creating chat: ${response.errorBody()?.string()}")
                    Toast.makeText(this@ContactDetailActivity, 
                        "Failed to create chat", 
                        Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Chat>, t: Throwable) {
                Log.e("CHAT_CREATE", "Network error: ${t.message}")
                Toast.makeText(this@ContactDetailActivity, 
                    "Network error: ${t.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        })
    }
}