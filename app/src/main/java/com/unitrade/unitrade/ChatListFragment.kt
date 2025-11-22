package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.databinding.FragmentChatListBinding
import com.unitrade.unitrade.ChatThread
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * app/src/main/java/com/unitrade/unitrade/ChatListFragment.kt
 * Menampilkan daftar percakapan (threads).
 */
@AndroidEntryPoint
class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()

    private lateinit var adapter: ChatListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentChatListBinding.bind(view)

        adapter = ChatListAdapter(mutableListOf()) { thread ->
            // fast path: navigate without SafeArgs using bundle
            val bundle = bundleOf("chatId" to thread.chatId)
            // ensure nav_graph has a destination fragment id "chatDetailFragment"
            findNavController().navigate(R.id.chatDetailFragment, bundle)
        }

        binding.rvThreads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvThreads.adapter = adapter

        // collect threads from viewModel (threads is StateFlow<List<ChatThread>>)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.threads.collectLatest { list -> adapter.setItems(list) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
