package com.example.soci_app.model

data class Chat(
    val id: Int,
    val user1_id: Int,
    val user2_id: Int,
    val last_message_at: String?,
    val created_at: String,
    val updated_at: String,
    val latest_message: Message?, // ✅ Matches latest_message object in JSON
    val user1: User, // ✅ Matches user1 object in JSON
    val user2: User // ✅ Matches user2 object in JSON
)
