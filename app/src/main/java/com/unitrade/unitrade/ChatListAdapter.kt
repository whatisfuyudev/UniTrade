package com.unitrade.unitrade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unitrade.unitrade.R
import com.unitrade.unitrade.ChatThread

/**
 * app/src/main/java/com/unitrade/unitrade/ui/chat/ChatListAdapter.kt
 * Adapter RecyclerView untuk daftar thread chat.
 */
class ChatListAdapter(
    private val items: MutableList<ChatThread> = mutableListOf(),
    private val onClick: (ChatThread) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    fun setItems(newItems: List<ChatThread>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_thread, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.bind(t)
        holder.itemView.setOnClickListener { onClick(t) }
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvLast: TextView = view.findViewById(R.id.tvLastMessage)
        fun bind(thread: ChatThread) {
            tvName.text = thread.participants.joinToString(", ") // adjust to show other user displayName in real app
            tvLast.text = thread.lastMessageText ?: "—"
        }
    }
}
