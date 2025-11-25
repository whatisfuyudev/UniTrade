package com.unitrade.unitrade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unitrade.unitrade.R

/**
 * app/src/main/java/com/unitrade/unitrade/ImagePagerAdapter.kt
 *
 * Adapter RecyclerView untuk ViewPager2 yang menampilkan gambar produk.
 * - menerima list url (String)
 * - callback onClick(url) dipanggil saat image diklik (untuk membuka ImageViewerActivity)
 */
class ImagePagerAdapter(
    private val items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<ImagePagerAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.pagerImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_image_pager, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val url = items[position]
        if (url.isNotBlank()) {
            Glide.with(holder.img)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.placeholder)
                .into(holder.img)
        } else {
            holder.img.setImageResource(R.drawable.placeholder)
        }

        holder.img.setOnClickListener { onClick(url) }
    }

    override fun getItemCount(): Int = items.size
}
