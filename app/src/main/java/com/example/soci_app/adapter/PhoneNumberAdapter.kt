package com.example.soci_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.R

class PhoneNumberAdapter(
    private val phoneNumbers: List<String>,
    private val onPhoneClick: (String) -> Unit
) : RecyclerView.Adapter<PhoneNumberAdapter.PhoneNumberViewHolder>() {

    class PhoneNumberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val phoneNumberTextView: TextView = itemView.findViewById(R.id.phoneNumberText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhoneNumberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phone_number, parent, false)
        return PhoneNumberViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhoneNumberViewHolder, position: Int) {
        val phoneNumber = phoneNumbers[position]
        holder.phoneNumberTextView.text = phoneNumber
        
        holder.itemView.setOnClickListener {
            onPhoneClick(phoneNumber)
        }
    }

    override fun getItemCount(): Int = phoneNumbers.size
}