package com.unitrade.unitrade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unitrade.unitrade.Product
import com.unitrade.unitrade.R
import java.text.NumberFormat
import java.util.Locale

/**
 * app/src/main/java/com/unitrade/unitrade/ui/profile/MyProductAdapter.kt
 *
 * Adapter RecyclerView khusus untuk "My Products" (halaman profil):
 * - Menampilkan foto, judul, harga, kondisi
 * - Tombol Edit (onEdit) dan Delete (onDelete) di setiap item
 * - Klik area item memicu onClick (navigasi ke detail)
 *
 * Callbacks:
 *  - onEdit(product)  : panggil saat tombol edit ditekan
 *  - onDelete(product): panggil saat tombol delete ditekan (fragment menampilkan konfirmasi)
 *  - onClick(product) : panggil saat item diklik (navigasi ke detail)
 *
 * Desain: tidak mengubah ProductAdapter yang dipakai di tempat lain.
 */
class MyProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit,
    private val onClick: (Product) -> Unit
) : ListAdapter<Product, MyProductAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) =
                oldItem.productId == newItem.productId

            override fun areContentsTheSame(oldItem: Product, newItem: Product) =
                oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgProductMy)
        val title: TextView = view.findViewById(R.id.tvTitleMy)
        val price: TextView = view.findViewById(R.id.tvPriceMy)
        val condition: TextView = view.findViewById(R.id.tvConditionMy)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditProduct)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteProduct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_my_product, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)

        holder.title.text = p.title
        holder.price.text = formatPrice(p.price)
        holder.condition.text = p.condition

        val imgUrl = p.imageUrls.firstOrNull()?.takeIf { it.isNotBlank() && it != "-" }
        if (imgUrl != null) {
            Glide.with(holder.img.context)
                .load(imgUrl)
                .centerCrop()
                .placeholder(R.drawable.placeholder)
                .into(holder.img)
        } else {
            Glide.with(holder.img.context)
                .load(R.drawable.placeholder)
                .into(holder.img)
        }

        holder.itemView.setOnClickListener { onClick(p) }
        holder.btnEdit.setOnClickListener { onEdit(p) }
        holder.btnDelete.setOnClickListener { onDelete(p) }
    }

    private fun formatPrice(value: Double): String {
        val nf = NumberFormat.getInstance(Locale("in", "ID"))
        return "Rp ${nf.format(value.toLong())}"
    }
}
