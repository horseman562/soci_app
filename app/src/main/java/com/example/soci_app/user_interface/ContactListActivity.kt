package com.example.soci_app.user_interface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R
import com.example.soci_app.adapter.ContactAdapter
import com.example.soci_app.model.Contact
import com.google.android.material.bottomnavigation.BottomNavigationView

class ContactListActivity : AppCompatActivity() {

    private lateinit var contactRecyclerView: RecyclerView
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var bottomNavigation: BottomNavigationView
    private val contacts = mutableListOf<Contact>()
    private val REQUEST_READ_CONTACTS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        contactRecyclerView = findViewById(R.id.contactRecyclerView)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        
        contactRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Initialize adapter
        contactAdapter = ContactAdapter(mutableListOf()) { contact ->
            val intent = Intent(this, ContactDetailActivity::class.java)
            intent.putExtra("contact_name", contact.name)
            intent.putStringArrayListExtra("contact_phones", ArrayList(contact.phoneNumbers))
            startActivity(intent)
        }
        contactRecyclerView.adapter = contactAdapter

        // Set current tab as selected
        bottomNavigation.selectedItemId = R.id.menu_contacts

        // Handle Bottom Navigation Clicks
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                R.id.menu_contacts -> {
                    // Already in contacts, do nothing
                }
                R.id.menu_profile -> {
                    Toast.makeText(this, "Profile coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ_CONTACTS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_READ_CONTACTS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadContacts()
                } else {
                    Toast.makeText(this, "Permission denied to read contacts", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadContacts() {
        Log.d("ContactList", "Starting to load contacts...")
        contacts.clear()
        
        val contactMap = mutableMapOf<String, MutableList<String>>()

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        Log.d("ContactList", "Cursor: $cursor")

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            Log.d("ContactList", "Name index: $nameIndex, Phone index: $phoneIndex")
            Log.d("ContactList", "Cursor count: ${it.count}")

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                var phone = it.getString(phoneIndex) ?: ""

                Log.d("ContactList", "Raw contact: Name='$name', Phone='$phone'")

                // Normalize phone number (remove spaces, dashes)
                phone = phone.replace("\\s".toRegex(), "").replace("-", "")

                if (name.isNotBlank() && phone.isNotBlank()) {
                    if (contactMap.containsKey(name)) {
                        if (!contactMap[name]!!.contains(phone)) {
                            contactMap[name]!!.add(phone)
                        }
                    } else {
                        contactMap[name] = mutableListOf(phone)
                    }
                    Log.d("ContactList", "Added contact: $name with phone $phone")
                }
            }
        }

        // Convert map to Contact objects
        for ((name, phoneNumbers) in contactMap) {
            contacts.add(Contact(name, phoneNumbers))
        }

        Log.d("ContactList", "Total contacts loaded: ${contacts.size}")

        // Add fallback for testing if no real contacts found
        if (contacts.isEmpty()) {
            Log.w("ContactList", "No contacts found, adding test contact")
            Toast.makeText(this, "No contacts found on device", Toast.LENGTH_LONG).show()
            
            // Add a test contact for debugging
            contacts.add(Contact("Test Contact", listOf("1234567890", "0987654321")))
        }

        // Update adapter with new contacts
        contactAdapter.updateContacts(contacts)
        
        Log.d("ContactList", "Adapter updated, item count: ${contactAdapter.itemCount}")
    }

}