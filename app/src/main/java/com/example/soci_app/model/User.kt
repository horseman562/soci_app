package com.example.soci_app.model

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val phone_number: String?,
    val email_verified_at: String?, // ✅ Nullable because API returns `null`
    val created_at: String,
    val updated_at: String
)
