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
                    // tampilkan teks kosong juga
                    binding.rvFavorites.visibility = View.GONE
                    binding.tvEmptyFavorites.visibility = View.VISIBLE
                    return@repeatOnLifecycle
                }

                try {
                    val user = userRepository.getCurrentUserOnce()
                    if (user == null) {
                        Toast.makeText(requireContext(), "Gagal memuat data user", Toast.LENGTH_SHORT).show()
                        binding.rvFavorites.visibility = View.GONE
                        binding.tvEmptyFavorites.visibility = View.VISIBLE
                        return@repeatOnLifecycle
                    }

                    if (user.favorites.isNullOrEmpty()) {
                        // tidak ada favorit
                        adapter.submitList(emptyList())
                        binding.rvFavorites.visibility = View.GONE
                        binding.tvEmptyFavorites.visibility = View.VISIBLE
                        return@repeatOnLifecycle
                    }

                    // ambil produk dari repository
                    val products = productRepository.getProductsByIds(user.favorites)

                    if (products.isNullOrEmpty()) {
                        // jika repository tidak menemukan produk apapun dari id favorit
                        adapter.submitList(emptyList())
                        binding.rvFavorites.visibility = View.GONE
                        binding.tvEmptyFavorites.visibility = View.VISIBLE
                    } else {
                        // ada produk, tampilkan list dan sembunyikan teks
                        adapter.submitList(products)
                        binding.rvFavorites.visibility = View.VISIBLE
                        binding.tvEmptyFavorites.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Gagal memuat favorit: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.rvFavorites.visibility = View.GONE
                    binding.tvEmptyFavorites.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
