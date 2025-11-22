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
 * File: app/src/main/java/com/unitrade/unitrade/ui/product/ImagePreviewAdapter.kt
 *
 * Adapter RecyclerView untuk menampilkan preview gambar yang dipilih sebelum upload.
 * - items: MutableList<Uri> yang berasal dari picker.
 * - onRemove: callback ketika user menekan tombol hapus pada posisi tertentu.
 *
 * Usage:
 *   val adapter = ImagePreviewAdapter(pickedUris) { pos ->
 *       // remove handled in fragment: pickedUris.removeAt(pos); adapter.notifyItemRemoved(pos)
 *   }
 */
class ImagePreviewAdapter(
    private val items: MutableList<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImagePreviewAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.imgPreview)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_image_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]

        // Load thumbnail with Glide (or Picasso). Glide auto handles Uri.
        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .placeholder(R.drawable.placeholder) // optional placeholder
            .into(holder.img)

        holder.btnRemove.setOnClickListener {
            // invoke callback so fragment can update underlying list and call notifyItemRemoved
            onRemove.invoke(holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size

    // helper to update adapter data safely
    fun setData(newList: List<Uri>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun addUri(uri: Uri) {
        items.add(uri)
        notifyItemInserted(items.size - 1)
    }
}
