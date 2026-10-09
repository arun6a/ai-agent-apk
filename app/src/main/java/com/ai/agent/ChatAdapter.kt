package com.ai.agent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(text: String) {
        if (messages.isNotEmpty()) {
            val last = messages.last()
            messages[messages.lastIndex] = last.copy(text = text, status = MessageStatus.DONE)
            notifyItemChanged(messages.lastIndex)
        }
    }

    /**
     * Remove the last message if it matches the given text.
     * Used to remove "Thinking..." placeholder when the first step appears.
     */
    fun removeLastIfEquals(expectedText: String) {
        if (messages.isNotEmpty() && messages.last().text == expectedText) {
            messages.removeAt(messages.lastIndex)
            notifyItemRemoved(messages.size)
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: View = itemView.findViewById(R.id.userMessageContainer)
        private val aiContainer: View = itemView.findViewById(R.id.aiMessageContainer)
        private val userText: TextView = itemView.findViewById(R.id.userMessageText)
        private val userMeta: TextView = itemView.findViewById(R.id.userMessageMeta)
        private val aiText: TextView = itemView.findViewById(R.id.aiMessageText)
        private val aiMeta: TextView = itemView.findViewById(R.id.aiMessageMeta)

        fun bind(message: ChatMessage) {
            if (message.isUser) {
                userContainer.visibility = View.VISIBLE
                aiContainer.visibility = View.GONE
                userText.text = message.text
                userMeta.text = timeFormat.format(Date(message.timestamp))
            } else {
                userContainer.visibility = View.GONE
                aiContainer.visibility = View.VISIBLE
                aiText.text = message.text
                val statusLabel = when (message.status) {
                    MessageStatus.PENDING -> "Waiting..."
                    MessageStatus.THINKING -> "Thinking..."
                    MessageStatus.DONE -> "AI"
                }
                aiMeta.text = "$statusLabel · ${timeFormat.format(Date(message.timestamp))}"
            }
        }
    }
}
