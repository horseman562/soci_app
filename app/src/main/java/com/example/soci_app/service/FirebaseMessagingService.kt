package com.example.soci_app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.soci_app.R
import com.example.soci_app.api.FcmTokenRequest
import com.example.soci_app.api.RetrofitClient
import com.example.soci_app.user_interface.ChatActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "New Message", it.body ?: "")
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val title = data["title"] ?: "New Message"
        val body = data["body"] ?: ""
        val chatId = data["chat_id"]
        val senderId = data["sender_id"]
        
        sendNotification(title, body, chatId, senderId)
    }

    private fun sendNotification(
        title: String, 
        messageBody: String, 
        chatId: String? = null, 
        senderId: String? = null
    ) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            chatId?.let { putExtra("chat_id", it) }
            senderId?.let { putExtra("sender_id", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "default_notification_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun sendRegistrationToServer(token: String) {
        Log.d(TAG, "FCM Registration Token: $token")
        
        val sharedPreferences: SharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("AUTH_TOKEN", null)
        
        if (authToken != null) {
            val authHeader = "Bearer $authToken"
            val request = FcmTokenRequest(token)
            
            RetrofitClient.instance.updateFcmToken(authHeader, request)
                .enqueue(object : Callback<com.example.soci_app.api.ApiResponse> {
                    override fun onResponse(
                        call: Call<com.example.soci_app.api.ApiResponse>,
                        response: Response<com.example.soci_app.api.ApiResponse>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "FCM token sent to server successfully")
                        } else {
                            Log.e(TAG, "Failed to send FCM token to server: ${response.errorBody()?.string()}")
                        }
                    }
                    
                    override fun onFailure(call: Call<com.example.soci_app.api.ApiResponse>, t: Throwable) {
                        Log.e(TAG, "Error sending FCM token to server: ${t.message}")
                    }
                })
        } else {
            Log.w(TAG, "No auth token available, cannot send FCM token to server")
        }
    }

    companion object {
        private const val TAG = "FCMService"
    }
}