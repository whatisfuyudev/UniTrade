package com.unitrade.unitrade

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

/**
 * app/src/main/java/com/unitrade/unitrade/ProductDetailFragment.kt
 *
 * Menampilkan detail produk. Saat tombol "Chat Penjual" ditekan:
 * - jika user belum login -> navigasi ke LoginFragment
 * - jika user login -> buat/ambil chat thread antara user dan penjual, lalu navigasi ke ChatDetailFragment
 *
 * Catatan:
 * - Memerlukan ChatRepository yang memiliki suspend fun getOrCreateThreadWith(userA, userB): String
 * - Pastikan ChatRepository di-provide/inject via Hilt di project.
 */
@AndroidEntryPoint
class ProductDetailFragment : Fragment(R.layout.fragment_product_detail) {

    @Inject
    lateinit var repository: ProductRepository

    // Inject ChatRepository untuk operasi chat
    @Inject
    lateinit var chatRepository: ChatRepository

    private var productId: String? = null
    private var currentProduct: Product? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        btnBack.setOnClickListener { findNavController().popBackStack() }

        btnFavorite.setOnClickListener {
            Toast.makeText(requireContext(), "Sedang on progress", Toast.LENGTH_SHORT).show()
        }
        btnSave.setOnClickListener {
            Toast.makeText(requireContext(), "Sedang on progress", Toast.LENGTH_SHORT).show()
        }

        // Chat button behaviour:
        btnChat.setOnClickListener {
            // disable sementara untuk mencegah klik ganda
            btnChat.isEnabled = false

            // Ambil user saat ini
            val currentUid = chatRepository.auth.currentUser?.uid
            if (currentUid == null) {
                // user belum login -> arahkan ke halaman login
                Toast.makeText(requireContext(), "Harap login terlebih dahulu untuk menghubungi penjual", Toast.LENGTH_SHORT).show()
                // navigasi ke loginFragment (pastikan nav_graph memiliki id loginFragment)
                findNavController().navigate(R.id.loginFragment)
                btnChat.isEnabled = true
                return@setOnClickListener
            }

            val ownerId = currentProduct?.ownerId
            if (ownerId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Informasi penjual tidak tersedia", Toast.LENGTH_SHORT).show()
                btnChat.isEnabled = true
                return@setOnClickListener
            }

            if (ownerId == currentUid) {
                Toast.makeText(requireContext(), "Ini adalah produk milik Anda", Toast.LENGTH_SHORT).show()
                btnChat.isEnabled = true
                return@setOnClickListener
            }

            // buat atau ambil thread (suspend) lalu navigasi ke chat detail
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val chatId = chatRepository.getOrCreateThreadWith(currentUid, ownerId)
                    // navigasi ke chatDetailFragment dengan arg chatId
                    val bundle = Bundle().apply { putString("chatId", chatId) }
                    findNavController().navigate(R.id.chatDetailFragment, bundle)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memulai chat: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    btnChat.isEnabled = true
                }
            }
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
