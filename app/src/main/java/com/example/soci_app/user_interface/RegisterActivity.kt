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
import com.example.soci_app.api.RetrofitClient
import com.example.soci_app.model.RegisterRequest
import com.example.soci_app.model.LoginResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val nameField = findViewById<EditText>(R.id.name)
        val emailField = findViewById<EditText>(R.id.email)
        val phoneField = findViewById<EditText>(R.id.phone)
        val passwordField = findViewById<EditText>(R.id.password)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginLink = findViewById<TextView>(R.id.loginLink)

        registerButton.setOnClickListener {
            
            val name = nameField.text.toString()
            val email = emailField.text.toString()
            val phone = phoneField.text.toString()
            val password = passwordField.text.toString()

            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                Log.d("Register", "Register")
                registerUser(name, email, phone.ifEmpty { null }, password)
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser(name: String, email: String, phone: String?, password: String) {
        Log.d("Register", "Dalam reg")
        val request = RegisterRequest(name, email, password, phone)

        RetrofitClient.instance.register(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val token = response.body()?.access_token
                    val userId = response.body()?.user?.id ?: -1

                    // Save token to SharedPreferences
                    val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putString("AUTH_TOKEN", token).putInt("USER_ID", userId).apply()

                    Toast.makeText(this@RegisterActivity, "Registration Successful!", Toast.LENGTH_SHORT).show()
                    Log.d("TOKEN", "Bearer $token")

                    // Navigate to Home Screen
                    val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "Registration Failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Log.d("RegisterActivity", "Error: ${t.message}")
                Toast.makeText(this@RegisterActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}