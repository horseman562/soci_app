package com.example.soci_app.user_interface
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.soci_app.R
import com.example.soci_app.api.RetrofitClient
import com.example.soci_app.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        //val logoutButton = findViewById<Button>(R.id.logoutButton)

        val sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("AUTH_TOKEN", null)

        if (token != null) {
            RetrofitClient.instance.getUser("Bearer $token")
                .enqueue(object : Callback<User> {
                    override fun onResponse(call: Call<User>, response: Response<User>) {
                        if (response.isSuccessful) {
                            val user = response.body()
                            welcomeText.text = "Welcome, ${user?.name}!"
                        }
                    }

                    override fun onFailure(call: Call<User>, t: Throwable) {
                        welcomeText.text = "Failed to load user data"
                    }
                })
        }

        // Handle logout button click

    }

}