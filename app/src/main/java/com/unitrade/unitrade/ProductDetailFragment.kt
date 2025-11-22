package com.unitrade.unitrade

/*
File: app/src/main/java/com/unitrade/unitrade/ui/product/detail/ProductDetailFragment.kt

Deskripsi:
Fragment untuk menampilkan detail produk individual.
- Mengambil productId dari arguments (bundle).
- Memanggil ProductRepository.getProductOnce(productId) (suspend) untuk memuat data.
- Menampilkan hero image, judul, harga, kondisi, deskripsi.
- Tombol Favorite & Chat menampilkan Toast sementara (fitur penuh akan diimplementasikan selanjutnya).
- Menggunakan Glide untuk memuat gambar.

Praktik modern:
- Hilt untuk ProductRepository injection.
- Memanggil fungsi suspend dengan viewLifecycleOwner.lifecycleScope.launch.
- Menggunakan view-binding via findViewById (bisa diganti dengan generated binding bila kamu gunakan).
*/

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.unitrade.unitrade.R
import com.unitrade.unitrade.Product
import com.unitrade.unitrade.ProductRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProductDetailFragment : Fragment(R.layout.fragment_product_detail) {

    @Inject
    lateinit var repository: ProductRepository

    private var productId: String? = null
    private var currentProduct: Product? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ambil productId dari arguments (diset saat navigate dari adapter)
        productId = arguments?.getString("productId")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val imgHero = view.findViewById<ImageView>(R.id.imgHero)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvPrice = view.findViewById<TextView>(R.id.tvPrice)
        val tvCondition = view.findViewById<TextView>(R.id.tvCondition)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val btnFavorite = view.findViewById<ImageButton>(R.id.btnFavorite)
        val btnChat = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChatSeller)
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        // Back navigation
        btnBack.setOnClickListener { findNavController().popBackStack() }

        // Placeholder behaviour for favourite/save/chat
        btnFavorite.setOnClickListener {
            Toast.makeText(requireContext(), "Sedang on progress", Toast.LENGTH_SHORT).show()
        }
        btnSave.setOnClickListener {
            Toast.makeText(requireContext(), "Sedang on progress", Toast.LENGTH_SHORT).show()
        }
        btnChat.setOnClickListener {
            Toast.makeText(requireContext(), "Membuka chat dengan penjual (fitur on progress)", Toast.LENGTH_SHORT).show()
        }

        // Load product detail (suspend function)
        productId?.let { id ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val product = repository.getProductOnce(id)
                    if (product != null) {
                        currentProduct = product
                        tvTitle.text = product.title
                        tvPrice.text = formatPrice(product.price)
                        tvCondition.text = product.condition
                        tvDescription.text = product.description

                        // Load image (ambil url pertama bila valid)
                        val url = product.imageUrls.firstOrNull()?.takeIf { it.isNotBlank() && it != "-" }
                        if (url != null) {
                            Glide.with(this@ProductDetailFragment)
                                .load(url)
                                .placeholder(R.drawable.placeholder)
                                .into(imgHero)
                        } else {
                            Glide.with(this@ProductDetailFragment)
                                .load(R.drawable.placeholder)
                                .into(imgHero)
                        }
                    } else {
                        Toast.makeText(requireContext(), "Produk tidak ditemukan", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memuat produk: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "productId tidak tersedia", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatPrice(value: Double): String {
        val formatted = java.text.NumberFormat.getInstance(java.util.Locale("in", "ID")).format(value.toLong())
        return "Rp $formatted"
    }
}
