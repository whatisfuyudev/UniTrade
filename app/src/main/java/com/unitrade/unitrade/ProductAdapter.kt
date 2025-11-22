package com.unitrade.unitrade

/*
File: app/src/main/java/com/unitrade/unitrade/ui/home/ProductAdapter.kt

Deskripsi:
RecyclerView ListAdapter untuk menampilkan daftar Product pada HomeFragment atau ProductListFragment.
- Menggunakan ListAdapter + DiffUtil untuk performa update yang efisien.
- Memuat gambar dengan Glide (atau library image loading lain).
- Menyediakan callback onItemClick untuk navigasi ke detail.

Catatan:
Pastikan ada placeholder drawable (placeholder) di res/drawable.
*/

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unitrade.unitrade.R
import com.unitrade.unitrade.Product
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val onItemClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.productId == newItem.productId
            override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgProduct)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val price: TextView = view.findViewById(R.id.tvPrice)
        val condition: TextView = view.findViewById(R.id.tvCondition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)

        holder.title.text = p.title
        holder.price.text = formatPrice(p.price)
        holder.condition.text = p.condition

        // Ambil url pertama yang valid; beberapa produk mungkin menggunakan "-" placeholder
        val imgUrl = p.imageUrls.firstOrNull()?.takeIf { it.isNotBlank() && it != "-" }
        if (imgUrl != null) {
            Glide.with(holder.img)
                .load(imgUrl)
                .centerCrop()
                .placeholder(R.drawable.placeholder)
                .into(holder.img)
        } else {
            // tampilkan placeholder lokal
            Glide.with(holder.img)
                .load(R.drawable.placeholder)
                .into(holder.img)
        }

        holder.itemView.setOnClickListener { onItemClick(p) }
    }

    private fun formatPrice(value: Double): String {
        val nf = NumberFormat.getInstance(Locale("in", "ID"))
        return "Rp ${nf.format(value.toLong())}"
    }
}
