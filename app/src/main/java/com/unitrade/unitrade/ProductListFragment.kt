package com.unitrade.unitrade

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.unitrade.unitrade.databinding.FragmentProductListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProductListFragment : Fragment() {

    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!

    // private val viewModel: ProductListViewModel by viewModels() // nanti aktifkan

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Example: grid 2 columns
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)

        // Example placeholder adapter setup (ganti productAdapter saat implementasi)
        // binding.rvProducts.adapter = productAdapter

        // If you want to add spacing between grid items, use ItemDecoration
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
