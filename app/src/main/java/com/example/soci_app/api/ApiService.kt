package com.example.soci_app.api

import com.example.soci_app.model.LoginRequest
import com.example.soci_app.model.LoginResponse
import com.example.soci_app.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET

interface ApiService {
    @POST("login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @GET("user")
    fun getUser(@Header("Authorization") token: String): Call<User>
}