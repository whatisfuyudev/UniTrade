package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.unitrade.unitrade.R
import com.unitrade.unitrade.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // private val viewModel: HomeViewModel by viewModels() // add when implemented

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)

        // toolbar menu
        val toolbar = binding.toolbar as MaterialToolbar
        toolbar.inflateMenu(R.menu.home_top_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    findNavController().navigate(R.id.action_home_to_search)
                    true
                }
                R.id.action_filter -> {
                    // show filter bottomsheet
                    // findNavController().navigate(R.id.action_home_to_filter)
                    true
                }
                else -> false
            }
        }

        // RecyclerView setup (placeholder adapter)
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        // binding.rvProducts.adapter = productAdapter

        binding.fabAddProduct.setOnClickListener {
            // navigate to add product screen or to bottom nav add action
            findNavController().navigate(R.id.action_home_to_addProduct)
        }

        binding.swipeRefresh.setOnRefreshListener {
            // trigger refresh in viewmodel
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
