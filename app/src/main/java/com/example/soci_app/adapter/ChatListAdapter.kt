package com.example.soci_app.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.model.Chat
import com.example.soci_app.R

class ChatListAdapter(
    private val chats: List<Chat>,
    private val currentUserId: Int, // Pass the actual user ID
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.tvUserName)
        val lastMessage: TextView = view.findViewById(R.id.tvLastMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]

        // ✅ Determine the correct chat partner
        val otherUser = if (chat.user1_id == currentUserId) chat.user2 else chat.user1

        // ✅ Prevent NullPointerException
        holder.userName.text = otherUser?.name ?: "Unknown User"
        holder.lastMessage.text = if (chat.latest_message != null) {
            chat.latest_message.message
        } else {
            "No messages yet"
        }

        // ✅ Handle chat item click with logging
        holder.itemView.setOnClickListener {
            Log.d("ChatClick", "Chat clicked: ID ${chat.id}")
            onChatClick(chat)
        }
    }

    override fun getItemCount(): Int = chats.size
}
