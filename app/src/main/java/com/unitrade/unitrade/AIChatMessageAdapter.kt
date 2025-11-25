package com.unitrade.unitrade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class AIChatMessageAdapter : ListAdapter<AIChatMessage, AIChatMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardUserMessage: CardView = itemView.findViewById(R.id.cardUserMessage)
        private val cardAiMessage: CardView = itemView.findViewById(R.id.cardAiMessage)
        private val textUserMessage: TextView = itemView.findViewById(R.id.textUserMessage)
        private val textUserTime: TextView = itemView.findViewById(R.id.textUserTime)
        private val textAiMessage: TextView = itemView.findViewById(R.id.textAiMessage)
        private val textAiTime: TextView = itemView.findViewById(R.id.textAiTime)

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(message: AIChatMessage) {
            if (message.isFromUser) {
                cardUserMessage.visibility = View.VISIBLE
                cardAiMessage.visibility = View.GONE
                textUserMessage.text = message.text
                textUserTime.text = timeFormat.format(message.timestamp.toDate())
            } else {
                cardUserMessage.visibility = View.GONE
                cardAiMessage.visibility = View.VISIBLE
                textAiMessage.text = message.text
                textAiTime.text = timeFormat.format(message.timestamp.toDate())
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<AIChatMessage>() {
        override fun areItemsTheSame(oldItem: AIChatMessage, newItem: AIChatMessage): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: AIChatMessage, newItem: AIChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
