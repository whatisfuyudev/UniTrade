package com.unitrade.unitrade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.unitrade.unitrade.R
import com.unitrade.unitrade.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * app/src/main/java/com/unitrade/unitrade/ui/chat/ChatMessageAdapter.kt
 *
 * Adapter RecyclerView untuk tampilan chat (in/out).
 * - Menentukan view type berdasarkan senderId vs current user uid.
 * - Menampilkan text atau image (atau keduanya).
 * - Klik pada image membuka image via ACTION_VIEW (browser / image viewer).
 *
 * Dependensi: Glide (sudah ada di build.gradle).
 */
class ChatMessageAdapter(
    private val context: Context
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

    companion object {
        private const val TYPE_IN = 0
        private const val TYPE_OUT = 1
    }

    private val currentUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        return if (msg.senderId == currentUid) TYPE_OUT else TYPE_IN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_OUT) {
            val v = inflater.inflate(R.layout.item_chat_message_out, parent, false)
            OutViewHolder(v)
        } else {
            val v = inflater.inflate(R.layout.item_chat_message_in, parent, false)
            InViewHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is InViewHolder) holder.bind(message)
        else if (holder is OutViewHolder) holder.bind(message)
    }

    // ViewHolder for incoming messages
    inner class InViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tvMessageTextIn)
        private val img: ImageView = view.findViewById(R.id.imgMessageIn)
        private val tvTime: TextView = view.findViewById(R.id.tvTimeIn)

        fun bind(msg: ChatMessage) {
            // Text
            if (!msg.text.isNullOrBlank()) {
                tvText.visibility = View.VISIBLE
                tvText.text = msg.text
            } else {
                tvText.visibility = View.GONE
            }

            // Image
            if (!msg.imageUrl.isNullOrBlank()) {
                img.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(msg.imageUrl)
                    .placeholder(R.drawable.placeholder)
                    .into(img)

                img.setOnClickListener {
                    openImage(msg.imageUrl)
                }
            } else {
                img.visibility = View.GONE
                img.setOnClickListener(null)
            }

            // Time
            tvTime.text = formatTimestamp(msg.createdAt)
        }
    }

    // ViewHolder for outgoing messages
    inner class OutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tvMessageTextOut)
        private val img: ImageView = view.findViewById(R.id.imgMessageOut)
        private val tvTime: TextView = view.findViewById(R.id.tvTimeOut)

        fun bind(msg: ChatMessage) {
            if (!msg.text.isNullOrBlank()) {
                tvText.visibility = View.VISIBLE
                tvText.text = msg.text
            } else {
                tvText.visibility = View.GONE
            }

            if (!msg.imageUrl.isNullOrBlank()) {
                img.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(msg.imageUrl)
                    .placeholder(R.drawable.placeholder)
                    .into(img)

                img.setOnClickListener {
                    openImage(msg.imageUrl)
                }
            } else {
                img.visibility = View.GONE
                img.setOnClickListener(null)
            }

            tvTime.text = formatTimestamp(msg.createdAt)
        }
    }

    // ganti openImage(...) implementation di ChatMessageAdapter
    private fun openImage(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            // mulai ImageViewerActivity (full screen, pinch-to-zoom)
            val intent = com.unitrade.unitrade.ImageViewerActivity.createIntent(context, url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // fallback: jika gagal, coba intent viewer eksternal
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(viewIntent)
            } catch (_: Exception) { /* ignore */ }
        }
    }


    private fun formatTimestamp(ts: Timestamp?): String {
        if (ts == null) return ""
        val date = ts.toDate()
        val sdf = SimpleDateFormat("HH:mm", Locale("in", "ID"))
        return sdf.format(date)
    }
}

private class ChatMessageDiffCallback : DiffUtil.ItemCallback<com.unitrade.unitrade.ChatMessage>() {
    override fun areItemsTheSame(oldItem: com.unitrade.unitrade.ChatMessage, newItem: com.unitrade.unitrade.ChatMessage): Boolean {
        return oldItem.messageId == newItem.messageId
    }

    override fun areContentsTheSame(oldItem: com.unitrade.unitrade.ChatMessage, newItem: com.unitrade.unitrade.ChatMessage): Boolean {
        return oldItem == newItem
    }
}
