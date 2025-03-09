package com.example.soci_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.soci_app.model.Message
import com.example.soci_app.R

class MessageAdapter(private val messages: MutableList<Message>, private val currentUserId: Int) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_ME) {
            R.layout.item_message  // ✅ Right-side bubble for me
        } else {
            R.layout.item_message_other  // ✅ Left-side bubble for others
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender_id == currentUserId) {
            VIEW_TYPE_ME  // ✅ My messages (right side)
        } else {
            VIEW_TYPE_OTHER  // ✅ Other messages (left side)
        }
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)

        fun bind(message: Message) {
            messageText.text = message.message
            messageTime.text = message.created_at // Format time if needed
        }
    }

    fun addMessage(newMessage: Message) {
        messages.add(newMessage)  // Add message to the list
        notifyItemInserted(messages.size - 1)  // Notify RecyclerView
    }

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2
    }
}
