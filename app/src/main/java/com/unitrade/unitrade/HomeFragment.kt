package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.unitrade.unitrade.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * File: app/src/main/java/com/unitrade/unitrade/ui/home/HomeFragment.kt
 *
 * Fungsi:
 * - Menampilkan daftar produk pada beranda (Home).
 * - Menyediakan toolbar (search + filter), FAB untuk tambah produk.
 * - Implementasi pagination (lazy-load) menggunakan ProductRepository.getProductsPage(...)
 * - Saat page pertama: adapter.submitList(page.items)
 * - Saat halaman berikutnya: adapter.addItems(page.items)
 *
 * Catatan:
 * - Memerlukan ProductRepository.getProductsPage(...) yang mengembalikan PageResult<Product>
 *   (lihat instruksi repository pagination yang diberikan sebelumnya).
 * - Memerlukan ProductAdapter versi yang menyediakan addItems(...) dan currentProducts.
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var productRepository: ProductRepository

    private lateinit var adapter: ProductAdapter

    // Pagination state
    private val pageSize = 10
    private var isLoading = false
    private var isLastPage = false
    private var lastVisibleSnapshot: com.google.firebase.firestore.DocumentSnapshot? = null
    private var orderDesc = true // default: newest first (Terbaru)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        setupToolbar()
        setupRecycler()
        setupFab()
        setupSwipeRefresh()

        // initial load (load first page explicitly)
        loadFirstPage()
    }

    private fun setupToolbar() {
        val toolbar = binding.toolbar as MaterialToolbar

        // Make toolbar visible and non-transparent
        toolbar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        toolbar.setTitleTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        toolbar.elevation = resources.getDimension(R.dimen.toolbar_elevation)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    findNavController().navigate(R.id.action_home_to_search)
                    true
                }
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                R.id.action_ai_chatbot -> {
                    findNavController().navigate(R.id.action_home_to_aiChatbot)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecycler() {
        adapter = ProductAdapter { product ->
            val bundle = Bundle().apply { putString("productId", product.productId) }
            findNavController().navigate(R.id.productDetailFragment, bundle)
        }

        val lm = LinearLayoutManager(requireContext())
        binding.rvProducts.layoutManager = lm
        binding.rvProducts.adapter = adapter

        // endless scroll listener: trigger loadNextPage() saat mendekati akhir
        binding.rvProducts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return // hanya saat scroll ke bawah

                val visibleItemCount = lm.childCount
                val totalItemCount = lm.itemCount
                val firstVisibleItemPosition = lm.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    val shouldLoadMore = (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3
                    if (shouldLoadMore) loadNextPage()
                }
            }
        })
    }

    private fun setupFab() {
        binding.fabAddProduct.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_addProduct)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            // user-triggered refresh: reset pagination dan reload
            resetAndLoad()
        }
    }

    /**
     * Load halaman pertama (gunakan submitList untuk mengganti list)
     */
    private fun loadFirstPage() {
        if (isLoading) return
        isLoading = true
        binding.swipeRefresh.isRefreshing = true

        // reset state
        lastVisibleSnapshot = null
        isLastPage = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = productRepository.getProductsPage(pageSize, null, orderDesc)
                // first page: replace list
                adapter.submitList(page.items)
                lastVisibleSnapshot = page.lastSnapshot
                isLastPage = page.items.size < pageSize
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * Load halaman berikutnya (append)
     * - Menggunakan adapter.addItems untuk append page baru
     */
    private fun loadNextPage() {
        if (isLoading || isLastPage) return
        isLoading = true
        binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = productRepository.getProductsPage(pageSize, lastVisibleSnapshot, orderDesc)
                if (page.items.isNotEmpty()) {
                    // append: gunakan addItems agar lebih efisien dan simpler
                    adapter.addItems(page.items)
                    lastVisibleSnapshot = page.lastSnapshot
                    if (page.items.size < pageSize) isLastPage = true
                } else {
                    // empty page -> tidak ada lagi
                    isLastPage = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun resetAndLoad() {
        // clear adapter and reload first page
        adapter.submitList(emptyList())
        lastVisibleSnapshot = null
        isLastPage = false
        loadFirstPage()
    }

    private fun showFilterDialog() {
        val choices = arrayOf("Terbaru", "Terlama")
        val checked = if (orderDesc) 0 else 1
        AlertDialog.Builder(requireContext())
            .setTitle("Urutkan")
            .setSingleChoiceItems(choices, checked) { dialog, which ->
                orderDesc = (which == 0)
                dialog.dismiss()
                // reset pagination dan reload dengan order baru
                resetAndLoad()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
