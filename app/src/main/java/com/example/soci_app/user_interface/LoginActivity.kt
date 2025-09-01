package com.example.soci_app.user_interface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.soci_app.R
import com.example.soci_app.api.FcmTokenRequest
import com.example.soci_app.api.RetrofitClient
import com.example.soci_app.model.LoginRequest
import com.example.soci_app.model.LoginResponse
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 🔥 Check if user is already logged in
        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("AUTH_TOKEN", null)

        if (token != null) {
            // 🔥 User already logged in, go to HomeActivity
            startActivity(Intent(this, HomeActivity::class.java))
            finish() // Prevent going back to login
        }

        setContentView(R.layout.activity_login)  // XML Layout File

        val emailField = findViewById<EditText>(R.id.email)
        val passwordField = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerLink = findViewById<TextView>(R.id.registerLink)

        loginButton.setOnClickListener {
            val email = emailField.text.toString()
            val password = passwordField.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Please enter email & password", Toast.LENGTH_SHORT).show()
            }
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Get FCM Token for testing - only if Play Services available
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            getFCMToken()
        } else {
            Log.w("FCM", "Google Play Services not available - FCM disabled")
        }
    }

    private fun loginUser(email: String, password: String) {
        val request = LoginRequest(email, password)

        RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val token = response.body()?.access_token
                    val userId = response.body()?.user?.id ?: -1

                    // Save token to SharedPreferences
                    val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putString("AUTH_TOKEN", token).putInt("USER_ID", userId).apply()

                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    Log.d("TOKEN", "Bearer $token")

                    // Send FCM token to server after successful login - only if Play Services available
                    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this@LoginActivity) == ConnectionResult.SUCCESS) {
                        sendFCMTokenToServer()
                    } else {
                        Log.w("FCM", "Skipping FCM token - Google Play Services not available")
                    }

                    // Navigate to Home Screen
                    val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Login Failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Log.d("MainActivity", "Errosssr: ${t.message}")
                Toast.makeText(this@LoginActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM", "FCM Registration token: $token")
            Toast.makeText(this, "FCM Token logged - check logcat", Toast.LENGTH_LONG).show()

            // TODO: Send token to your server to store in database
        }
    }

    private fun sendFCMTokenToServer() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val fcmToken = task.result
            Log.d("FCM", "Sending FCM token to server: $fcmToken")
            
            val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            val authToken = sharedPreferences.getString("AUTH_TOKEN", null)
            
            if (authToken != null) {
                val authHeader = "Bearer $authToken"
                val request = FcmTokenRequest(fcmToken)
                
                RetrofitClient.instance.updateFcmToken(authHeader, request)
                    .enqueue(object : Callback<com.example.soci_app.api.ApiResponse> {
                        override fun onResponse(
                            call: Call<com.example.soci_app.api.ApiResponse>,
                            response: Response<com.example.soci_app.api.ApiResponse>
                        ) {
                            if (response.isSuccessful) {
                                Log.d("FCM", "FCM token sent to server successfully")
                            } else {
                                Log.e("FCM", "Failed to send FCM token: ${response.errorBody()?.string()}")
                            }
                        }
                        
                        override fun onFailure(call: Call<com.example.soci_app.api.ApiResponse>, t: Throwable) {
                            Log.e("FCM", "Error sending FCM token: ${t.message}")
                        }
                    })
            }
        }
    }

}