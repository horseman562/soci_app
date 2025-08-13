package com.example.soci_app.user_interface

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R
import com.example.soci_app.adapter.PhoneNumberAdapter

class ContactDetailActivity : AppCompatActivity() {

    private lateinit var contactNameTextView: TextView
    private lateinit var phoneNumbersRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_detail)

        contactNameTextView = findViewById(R.id.contactNameTextView)
        phoneNumbersRecyclerView = findViewById(R.id.phoneNumbersRecyclerView)

        phoneNumbersRecyclerView.layoutManager = LinearLayoutManager(this)

        val contactName = intent.getStringExtra("contact_name") ?: "Unknown"
        val phoneNumbers = intent.getStringArrayListExtra("contact_phones") ?: arrayListOf()

        contactNameTextView.text = contactName

        phoneNumbersRecyclerView.adapter = PhoneNumberAdapter(phoneNumbers) { phoneNumber ->
            // Handle phone number selection (e.g., call, message)
        }
    }
}