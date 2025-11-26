package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.databinding.FragmentSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * File: app/src/main/java/com/unitrade/unitrade/SearchFragment.kt
 * Fungsi: mencari produk berdasarkan title/description, menampilkan hasil di RecyclerView.
 * Perbaikan: pastikan pencarian case-insensitive dengan memfilter hasil dari repository
 *             menggunakan perbandingan lowercased di sisi client.
 */
@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var productRepository: ProductRepository

    private lateinit var adapter: ProductAdapter
    private var searchJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)

        // toolbar back
        binding.searchToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        adapter = ProductAdapter { product ->
            val bundle = Bundle().apply { putString("productId", product.productId) }
            findNavController().navigate(R.id.productDetailFragment, bundle)
        }

        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        // initial UI state
        binding.tvEmpty.visibility = View.GONE
        binding.progress.visibility = View.GONE
        binding.rvResults.visibility = View.GONE

        binding.etSearch.addTextChangedListener { editable ->
            val text = editable?.toString() ?: ""
            // debounce to avoid too many queries
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300) // 300ms debounce
                performSearch(text)
            }
        }
    }

    private suspend fun performSearchCoroutine(q: String) {
        binding.progress.visibility = View.VISIBLE
        try {
            // ambil hasil dari repository (repository boleh melakukan prefix query);
            // lakukan filter tambahan di client untuk memastikan CASE-INSENSITIVE substring match
            val rawResults = productRepository.searchProductsByText(q, limit = 50)

            // case-insensitive filter di client
            val lower = q.lowercase(Locale.getDefault())
            val filtered = if (lower.isBlank()) {
                rawResults
            } else {
                rawResults.filter { prod ->
                    val title = prod.title ?: ""
                    val desc = prod.description ?: ""
                    title.lowercase(Locale.getDefault()).contains(lower) ||
                            desc.lowercase(Locale.getDefault()).contains(lower)
                }
            }

            if (filtered.isEmpty()) {
                // no results: hide recyclerView and show informative message
                binding.rvResults.visibility = View.GONE
                val displayText = if (q.isBlank()) {
                    "Ketik kata kunci untuk mencari produk."
                } else {
                    val safe = q.replace("\"", "'")
                    "Tidak ditemukan produk untuk \"$safe\".\nCoba kata kunci lain atau cek ejaan."
                }
                binding.tvEmpty.text = displayText
                binding.tvEmpty.visibility = View.VISIBLE
            } else {
                // show results
                binding.tvEmpty.visibility = View.GONE
                binding.rvResults.visibility = View.VISIBLE
                adapter.submitList(filtered)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.rvResults.visibility = View.GONE
            binding.tvEmpty.text = "Terjadi kesalahan saat mencari."
            binding.tvEmpty.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Gagal mencari: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            binding.progress.visibility = View.GONE
        }
    }

    private fun performSearch(q: String) {
        // early exit for empty input: show instruction text in center
        if (q.isBlank()) {
            adapter.submitList(emptyList())
            binding.rvResults.visibility = View.GONE
            binding.tvEmpty.text = "Ketik kata kunci untuk mencari produk."
            binding.tvEmpty.visibility = View.VISIBLE
            binding.progress.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            performSearchCoroutine(q)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}
