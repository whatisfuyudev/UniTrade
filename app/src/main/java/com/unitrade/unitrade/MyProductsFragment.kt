package com.unitrade.unitrade


import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.R
import com.unitrade.unitrade.databinding.FragmentMyProductsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * app/src/main/java/com/unitrade/unitrade/ui/profile/MyProductsFragment.kt
 *
 * List produk milik user. Menggunakan repeatOnLifecycle untuk pemanggilan suspend yang lifecycle-safe.
 * Menampilkan semua produk yang diupload oleh user:
 * - menampilkan list produk milik user (query by ownerId)
 * - tombol Edit -> navigasi ke AddEditProductFragment dengan productId (reuse add/edit fragment)
 * - tombol Delete -> munculkan konfirmasi -> panggil ProductRepository.deleteProduct(...) dan delete image via Cloudinary (recommended via backend)
 */
@AndroidEntryPoint
class MyProductsFragment : Fragment(R.layout.fragment_my_products) {

    private var _binding: FragmentMyProductsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var productRepository: com.unitrade.unitrade.ProductRepository
    @Inject lateinit var userRepository: com.unitrade.unitrade.UserRepository

    private lateinit var adapter: com.unitrade.unitrade.MyProductAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMyProductsBinding.bind(view)

        adapter = com.unitrade.unitrade.MyProductAdapter(
            onEdit = { product ->
                val bundle = Bundle().apply { putString("productId", product.productId) }
                findNavController().navigate(R.id.addEditProductFragment, bundle)
            },
            onDelete = { product ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Hapus produk")
                    .setMessage("Yakin ingin menghapus produk ini?")
                    .setPositiveButton("Hapus") { _, _ ->
                        // hapus dengan coroutine singkat (tidak perlu repeatOnLifecycle)
                        lifecycleScope.launch {
                            try {
                                productRepository.deleteProduct(product.productId, product.imageUrls)
                                Toast.makeText(requireContext(), "Produk dihapus", Toast.LENGTH_SHORT).show()
                                // reload
                                loadMyProductsOnce()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Gagal menghapus: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            },
            onClick = { product ->
                val bundle = Bundle().apply { putString("productId", product.productId) }
                findNavController().navigate(R.id.productDetailFragment, bundle)
            }
        )

        binding.rvMyProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyProducts.adapter = adapter

        // load list products in a lifecycle-safe way
        loadMyProducts()
    }

    private fun loadMyProducts() {
        // lifecycle safe pattern
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val uid = userRepository.currentUid() ?: return@repeatOnLifecycle
                try {
                    val list = productRepository.getProductsByOwner(uid)
                    adapter.submitList(list)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memuat produk: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // helper: load once (dipanggil setelah delete untuk refresh cepat)
    private fun loadMyProductsOnce() {
        lifecycleScope.launch {
            val uid = userRepository.currentUid() ?: return@launch
            try {
                val list = productRepository.getProductsByOwner(uid)
                adapter.submitList(list)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat produk: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
