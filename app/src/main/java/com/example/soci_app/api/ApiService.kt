package com.example.soci_app.api

import com.example.soci_app.model.LoginRequest
import com.example.soci_app.model.LoginResponse
import com.example.soci_app.model.User
import com.example.soci_app.model.Chat
import com.example.soci_app.model.Message
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @POST("login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @GET("user")
    fun getUser(@Header("Authorization") token: String): Call<User>

    @GET("chats")
    fun getRecentChats(@Header("Authorization") token: String): Call<List<Chat>>

    @POST("chats")
    fun createChat(@Header("Authorization") token: String, @Body request: CreateChatRequest): Call<Chat>

    @GET("chats/{chatId}/messages")
    fun getMessages(@Path("chatId") chatId: Int): Call<List<Message>>

    @POST("chats/{chatId}/messages")
    fun sendMessage(@Path("chatId") chatId: Int, @Body message: Message): Call<Message>
}

data class CreateChatRequest(val user1_id: Int, val user2_id: Int)