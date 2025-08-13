package com.example.soci_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R
import com.example.soci_app.model.Contact

class ContactAdapter(
    private var contacts: MutableList<Contact>,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private var filteredContacts: MutableList<Contact> = contacts.toMutableList()

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.contactName)
        val phoneTextView: TextView = itemView.findViewById(R.id.contactPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = filteredContacts[position]
        holder.nameTextView.text = contact.name
        holder.phoneTextView.text = "${contact.phoneNumbers.size} number(s)"
        
        holder.itemView.setOnClickListener {
            onContactClick(contact)
        }
    }

    override fun getItemCount(): Int = filteredContacts.size

    fun filter(query: String) {
        filteredContacts.clear()
        if (query.isEmpty()) {
            filteredContacts.addAll(contacts)
        } else {
            filteredContacts.addAll(contacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                contact.phoneNumbers.any { phone -> phone.contains(query) }
            })
        }
        notifyDataSetChanged()
    }

    fun updateContacts(newContacts: List<Contact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        filteredContacts.clear()
        filteredContacts.addAll(newContacts)
        notifyDataSetChanged()
    }
}