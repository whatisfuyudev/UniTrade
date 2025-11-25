package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.R
import com.unitrade.unitrade.UserRepository
import com.unitrade.unitrade.databinding.FragmentFavoritesBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * app/src/main/java/com/unitrade/unitrade/ui/profile/FavoritesFragment.kt
 *
 * Menampilkan daftar produk favorit user:
 * - ambil favorites array dari user doc (suspend)
 * - query productRepository.getProductsByIds(...) untuk ambil produk terkait (suspend)
 * - adapter: klik item -> navigasi ke ProductDetailFragment
 *
 * Perubahan:
 * - mengganti deprecated launchWhenStarted dengan lifecycleScope + repeatOnLifecycle
 */
@AndroidEntryPoint
class FavoritesFragment : Fragment(R.layout.fragment_favorites) {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var productRepository: com.unitrade.unitrade.ProductRepository

    private lateinit var adapter: com.unitrade.unitrade.ProductAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentFavoritesBinding.bind(view)

        adapter = com.unitrade.unitrade.ProductAdapter { product ->
            // navigate to product detail using argument productId
            val bundle = Bundle().apply { putString("productId", product.productId) }
            findNavController().navigate(R.id.productDetailFragment, bundle)
        }

        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = adapter

        loadFavorites()
    }

    private fun loadFavorites() {
        // safe coroutine usage with lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val uid = userRepository.currentUid()
                if (uid == null) {
                    Toast.makeText(requireContext(), "Silakan login", Toast.LENGTH_SHORT).show()
                    return@repeatOnLifecycle
                }

                try {
                    // getCurrentUserOnce() diasumsikan suspend
                    val user = userRepository.getCurrentUserOnce()
                    if (user == null) {
                        Toast.makeText(requireContext(), "Gagal memuat data user", Toast.LENGTH_SHORT).show()
                        return@repeatOnLifecycle
                    }

                    if (user.favorites.isNullOrEmpty()) {
                        adapter.submitList(emptyList())
                        return@repeatOnLifecycle
                    }

                    // gunakan fungsi baru di ProductRepository untuk ambil produk by ids
                    val products = productRepository.getProductsByIds(user.favorites)
                    adapter.submitList(products)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Gagal memuat favorit: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
