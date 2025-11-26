package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/ui/home/ProductAdapter.kt
 Fungsi:
 - Adapter RecyclerView untuk menampilkan list Product pada HomeFragment/ProductListFragment.
 - Menggunakan ListAdapter + DiffUtil untuk update efisien.
 - Menyediakan helper addItems(...) untuk pagination append.
 - Membersihkan target Glide pada onViewRecycled untuk mencegah flicker/leak.
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
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val onItemClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    init {
        // aktifkan stable ids untuk sedikit meningkatkan performa jika ingin
        setHasStableIds(true)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) =
                oldItem.productId == newItem.productId

            override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem == newItem
        }
    }

    // expose current list safely (ListAdapter sudah menyediakan currentList, ini wrapper nyaman)
    val currentProducts: List<Product>
        get() = super.getCurrentList()

    // helper untuk append (pagination)
    fun addItems(newItems: List<Product>) {
        if (newItems.isEmpty()) return
        // create a new list to avoid mutating underlying currentList
        val merged = ArrayList(currentProducts)
        merged.addAll(newItems)
        submitList(merged)
    }

    // jika ingin mengganti seluruh list (mis. saat filter berubah), gunakan submitList(newList)

    override fun getItemId(position: Int): Long {
        // stable id based on productId hash; fallback to position if productId kosong
        val pid = try {
            getItem(position).productId
        } catch (e: Exception) {
            null
        }
        return pid?.hashCode()?.toLong() ?: position.toLong()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgProduct)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val price: TextView = view.findViewById(R.id.tvPrice)
        val condition: TextView = view.findViewById(R.id.tvCondition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)

        holder.title.text = p.title ?: ""
        holder.price.text = formatPriceSafe(p.price)
        holder.condition.text = p.condition ?: ""

        // Image: ambil url pertama yang valid
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

    // clear image request ketika view direcycle untuk mencegah referensi drawable yang salah
    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(holder.img).clear(holder.img)
    }

    private fun formatPriceSafe(value: Double?): String {
        return try {
            val v = value ?: 0.0
            val nf = NumberFormat.getInstance(Locale("in", "ID"))
            "Rp ${nf.format(v.toLong())}"
        } catch (e: Exception) {
            "Rp 0"
        }
    }
}
