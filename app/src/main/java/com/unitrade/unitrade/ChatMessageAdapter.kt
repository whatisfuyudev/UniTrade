package com.unitrade.unitrade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.unitrade.unitrade.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * app/src/main/java/com/unitrade/unitrade/ChatMessageAdapter.kt
 *
 * Adapter RecyclerView untuk chat dengan dukungan:
 * - date header (pemisah per-hari)
 * - pesan masuk / keluar (text + optional image)
 * - klik gambar membuka ImageViewerActivity (full-screen, pinch-to-zoom)
 *
 * File ini mempertahankan perilaku lama (menampilkan text/time/image) dan menambahkan
 * fungsi submitMessageList(messages: List<ChatMessage>) yang mengubah daftar pesan
 * menjadi item dengan header tanggal.
 */
class ChatMessageAdapter(private val context: Context) :
    ListAdapter<ChatListItem, RecyclerView.ViewHolder>(ChatListItemDiff()) {

    companion object {
        private const val TYPE_DATE = 0
        private const val TYPE_IN = 1
        private const val TYPE_OUT = 2
    }

    private val currentUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatListItem.DateHeader -> TYPE_DATE
            is ChatListItem.MessageItem -> {
                val msg = (getItem(position) as ChatListItem.MessageItem).message
                if (msg.senderId == currentUid) TYPE_OUT else TYPE_IN
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE -> {
                val v = inf.inflate(R.layout.item_chat_date_header, parent, false)
                DateVH(v)
            }
            TYPE_OUT -> {
                val v = inf.inflate(R.layout.item_chat_message_out, parent, false)
                OutVH(v)
            }
            else -> {
                val v = inf.inflate(R.layout.item_chat_message_in, parent, false)
                InVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListItem.DateHeader -> (holder as DateVH).bind(item.title)
            is ChatListItem.MessageItem -> {
                val msg = item.message
                if (holder is InVH) holder.bind(msg)
                else if (holder is OutVH) holder.bind(msg)
            }
        }
    }

    /**
     * Transform a plain list of ChatMessage into a list containing date headers and message items,
     * then submit to ListAdapter.
     */
    fun submitMessageList(messages: List<ChatMessage>) {
        val out = mutableListOf<ChatListItem>()
        var lastDayKey: String? = null
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTitle = SimpleDateFormat("d MMM yyyy", Locale("in", "ID"))
        for (m in messages) {
            val ts = m.createdAt?.toDate() ?: Date()
            val dayKey = sdfKey.format(ts)
            if (dayKey != lastDayKey) {
                out.add(ChatListItem.DateHeader(sdfTitle.format(ts)))
                lastDayKey = dayKey
            }
            out.add(ChatListItem.MessageItem(m))
        }
        submitList(out)
    }

    // ViewHolders
    inner class DateVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvDateHeader)
        fun bind(text: String) { tv.text = text }
    }

    inner class InVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tvMessageTextIn)
        private val img: ImageView = view.findViewById(R.id.imgMessageIn)
        private val tvTime: TextView = view.findViewById(R.id.tvTimeIn)

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

                img.setOnClickListener { openImage(msg.imageUrl) }
            } else {
                img.visibility = View.GONE
                img.setOnClickListener(null)
            }

            tvTime.text = formatTimestamp(msg.createdAt)
        }
    }

    inner class OutVH(view: View) : RecyclerView.ViewHolder(view) {
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

                img.setOnClickListener { openImage(msg.imageUrl) }
            } else {
                img.visibility = View.GONE
                img.setOnClickListener(null)
            }

            tvTime.text = formatTimestamp(msg.createdAt)
        }
    }

    private fun openImage(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val intent = ImageViewerActivity.createIntent(context, url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
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

// sealed items and diff util
sealed class ChatListItem {
    data class DateHeader(val title: String) : ChatListItem()
    data class MessageItem(val message: ChatMessage) : ChatListItem()
}

class ChatListItemDiff : DiffUtil.ItemCallback<ChatListItem>() {
    override fun areItemsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean {
        return if (oldItem is ChatListItem.DateHeader && newItem is ChatListItem.DateHeader) {
            oldItem.title == newItem.title
        } else if (oldItem is ChatListItem.MessageItem && newItem is ChatListItem.MessageItem) {
            oldItem.message.messageId == newItem.message.messageId
        } else false
    }

    override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean {
        return oldItem == newItem
    }
}
