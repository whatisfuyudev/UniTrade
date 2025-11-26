package com.unitrade.unitrade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.unitrade.unitrade.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * app/src/main/java/com/unitrade/unitrade/ChatListAdapter.kt
 *
 * ChatListAdapter
 * - Lokasi: app/src/main/java/com/unitrade/unitrade/ui/chat/ChatListAdapter.kt
 * - Tanggung jawab:
 *   - Menampilkan daftar percakapan dengan tampilan:
 *     product image (first image) atau placeholder,
 *     product title (atau "Chat dengan Penjual" bila tidak ada),
 *     last message text,
 *     last message time (HH:mm).
 *   - onClick mengirim ChatListDisplay kembali ke caller.
 *
 * Catatan kompatibilitas:
 * - Model tampilan: ChatListDisplay (didefinisikan di kelas ini).
 * - Jika kamu memakai ChatListFragment yang saya kirim sebelumnya, fragment sudah
 *   mengisi ChatListDisplay dan memanggil adapter.setItems(...)
 */
class ChatListAdapter(
    private val items: MutableList<ChatListDisplay> = mutableListOf(),
    private val onClick: (ChatListDisplay) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    data class ChatListDisplay(
        val chatId: String,
        val productTitle: String?,
        val productImage: String?,
        val sellerId: String?,
        val lastMessageText: String?,
        val lastMessageAt: Timestamp?
    )

    fun setItems(newItems: List<ChatListDisplay>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_thread, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvLastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)

        fun bind(d: ChatListDisplay) {
            // Title: show product title if available, otherwise fallback to seller id
            tvName.text = d.productTitle ?: ("Chat dengan " + (d.sellerId ?: "Penjual"))

            // Last message text
            tvLastMessage.text = d.lastMessageText ?: ""

            // Product image: show first image if exists, otherwise launcher icon placeholder
            if (!d.productImage.isNullOrBlank()) {
                Glide.with(imgAvatar.context)
                    .load(d.productImage)
                    .placeholder(R.drawable.placeholder)
                    .circleCrop()
                    .into(imgAvatar)
            } else {
                imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            }

            // Time formatting HH:mm
            tvTime.text = formatTime(d.lastMessageAt)
        }

        private fun formatTime(ts: Timestamp?): String {
            if (ts == null) return ""
            val date = ts.toDate()
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(date)
        }
    }
}
