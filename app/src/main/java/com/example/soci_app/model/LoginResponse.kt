package com.example.soci_app.model

data class LoginResponse(
    val access_token: String, val user: User
)