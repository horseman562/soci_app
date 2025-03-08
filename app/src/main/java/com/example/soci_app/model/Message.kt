package com.example.soci_app.model

data class Message(
    val id: Int,
    val chat_id: Int,
    val sender_id: Int,
    val message: String,
    val created_at: String
)
