package com.example.soci_app.user_interface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R
import com.example.soci_app.adapter.ContactAdapter
import com.example.soci_app.model.Contact

class ContactListActivity : AppCompatActivity() {

    private lateinit var contactRecyclerView: RecyclerView
    private val contacts = mutableListOf<Contact>()
    private val REQUEST_READ_CONTACTS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        contactRecyclerView = findViewById(R.id.contactRecyclerView)
        contactRecyclerView.layoutManager = LinearLayoutManager(this)

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
        contacts.clear()
        
        val contactMap = mutableMapOf<String, MutableList<String>>()

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                var phone = it.getString(phoneIndex) ?: ""

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
                }
            }
        }

        // Convert map to Contact objects
        for ((name, phoneNumbers) in contactMap) {
            contacts.add(Contact(name, phoneNumbers))
        }

        contactRecyclerView.adapter = ContactAdapter(contacts) { contact ->
            val intent = Intent(this, ContactDetailActivity::class.java)
            intent.putExtra("contact_name", contact.name)
            intent.putStringArrayListExtra("contact_phones", ArrayList(contact.phoneNumbers))
            startActivity(intent)
        }
    }
}