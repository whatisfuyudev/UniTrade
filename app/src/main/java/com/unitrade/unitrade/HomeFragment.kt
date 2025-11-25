package com.unitrade.unitrade

/*
File: app/src/main/java/com/unitrade/unitrade/ui/home/HomeFragment.kt

Deskripsi:
Fragment yang menampilkan beranda aplikasi (daftar produk).
- Meng-collect productsFlow() dari ProductRepository secara lifecycle-aware menggunakan
  repeatOnLifecycle(Lifecycle.State.STARTED).
- Mengatur RecyclerView dan adapter (ProductAdapter).
- Menangani klik item (navigasi ke ProductDetailFragment) dan FAB.
- Menghapus referensi binding pada onDestroyView untuk menghindari memory leak.

Praktik modern:
- Menggunakan Hilt untuk inject ProductRepository.
- Menggunakan viewBinding (FragmentHomeBinding).
- Menggunakan repeatOnLifecycle untuk collect Flow agar aman terhadap lifecycle.
*/

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.unitrade.unitrade.R
import com.unitrade.unitrade.ProductRepository
import com.unitrade.unitrade.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var productRepository: ProductRepository

    private lateinit var adapter: ProductAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)

        // Toolbar menu handling (search/filter)
        val toolbar = binding.toolbar as MaterialToolbar
        toolbar.inflateMenu(R.menu.home_top_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    findNavController().navigate(R.id.action_home_to_search)
                    true
                }
                R.id.action_filter -> {
                    // TODO: tampilkan filter sheet
                    true
                }
                R.id.action_ai_chatbot -> {
                    findNavController().navigate(R.id.action_home_to_aiChatbot)
                    true
                }
                else -> false
            }
        }

        // Setup RecyclerView and adapter
        adapter = ProductAdapter { product ->
            // navigasi ke detail, kirimkan productId dalam bundle
            val bundle = Bundle().apply { putString("productId", product.productId) }
            findNavController().navigate(R.id.productDetailFragment, bundle)
        }
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter

        // FAB action
        binding.fabAddProduct.setOnClickListener {
            // TODO: navigasi ke AddEditProductFragment
            findNavController().navigate(R.id.action_home_to_addProduct)
        }

        // SwipeRefresh: UX only (data realtime akan diupdate otomatis)
        binding.swipeRefresh.setOnRefreshListener {
            // UX: hentikan spinner segera; data diperbarui oleh Flow listener
            binding.swipeRefresh.isRefreshing = false
        }

        // Collect products Flow lifecycle-aware
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                productRepository.productsFlow().collect { products ->
                    adapter.submitList(products)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
