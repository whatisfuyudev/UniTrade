package com.unitrade.unitrade

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unitrade.unitrade.R

/**
 * app/src/main/java/com/unitrade/unitrade/MixedImagePreviewAdapter.kt
 *
 * Adapter RecyclerView untuk preview gambar campuran (remote URLs + local Uris) pada Add/Edit Product (EDIT MODE).
 *
 * Fungsi:
 * - Menampilkan urutan campuran gambar remote (ImageItem.Remote(url)) dan gambar lokal (ImageItem.Local(uri)).
 * - Menyediakan callback khusus saat menghapus remote atau local agar pemilik Fragment bisa
 *   melakukan tindakan (mis. menghapus publicId di Cloudinary, atau update UI/internal list).
 * - onClick pada gambar memanggil callback onClickImage(item) (digunakan untuk membuka ImageViewerActivity).
 *
 * Letak file:
 * - app/src/main/java/com/unitrade/unitrade/MixedImagePreviewAdapter.kt
 */
sealed class ImageItem {
    data class Remote(val url: String) : ImageItem()
    data class Local(val uri: Uri) : ImageItem()
}

class MixedImagePreviewAdapter(
    val items: MutableList<ImageItem> = mutableListOf(),
    private val onRemoveRemote: (indexInRemoteList: Int, url: String) -> Unit = { _, _ -> },
    private val onRemoveLocal: (indexInLocalList: Int, uri: Uri) -> Unit = { _, _ -> },
    private val onClickImage: (item: ImageItem) -> Unit = {}
) : RecyclerView.Adapter<MixedImagePreviewAdapter.VH>() {

    companion object {
        private const val TYPE_REMOTE = 0
        private const val TYPE_LOCAL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ImageItem.Remote -> TYPE_REMOTE
            is ImageItem.Local -> TYPE_LOCAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_image_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        when (item) {
            is ImageItem.Remote -> {
                Glide.with(holder.img).load(item.url).centerCrop().placeholder(R.drawable.placeholder).into(holder.img)
                holder.img.setOnClickListener { onClickImage(item) }
                holder.btnRemove.setOnClickListener {
                    // compute index among remote items (useful for caller)
                    val idxRemote = items.subList(0, position + 1).count { it is ImageItem.Remote } - 1
                    onRemoveRemote(idxRemote, item.url)
                    items.removeAt(position)
                    notifyItemRemoved(position)
                }
            }
            is ImageItem.Local -> {
                Glide.with(holder.img).load(item.uri).centerCrop().placeholder(R.drawable.placeholder).into(holder.img)
                holder.img.setOnClickListener { onClickImage(item) }
                holder.btnRemove.setOnClickListener {
                    val idxLocal = items.subList(0, position + 1).count { it is ImageItem.Local } - 1
                    onRemoveLocal(idxLocal, item.uri)
                    items.removeAt(position)
                    notifyItemRemoved(position)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addLocal(uri: Uri) {
        items.add(ImageItem.Local(uri))
        notifyItemInserted(items.size - 1)
    }

    fun addRemote(url: String) {
        items.add(ImageItem.Remote(url))
        notifyItemInserted(items.size - 1)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgPreview)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }
}
