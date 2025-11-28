package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.databinding.FragmentChatListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * app/src/main/java/com/unitrade/unitrade/ChatListFragment.kt
 *
 * Menampilkan daftar percakapan (threads) - ditingkatkan:
 * - Untuk setiap ChatThread, bila ada productId -> fetch Product via ProductRepository
 *   dan tampilkan product title + first image + seller info di adapter.
 * - Tetap menggunakan ChatListAdapter sebagai UI adapter. Adapter diharapkan menyediakan
 *   tipe ChatListAdapter.ChatListDisplay sebagai model tampilan (lihat ChatListAdapter).
 *
 * Catatan:
 * - Saya tidak menghapus fungsionalitas lama, hanya menambahkan langkah fetch product per-thread.
 * - Pastikan ChatListAdapter versi di project mendukung ChatListDisplay seperti digunakan di bawah.
 */
@AndroidEntryPoint
class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatListAdapter

    // Inject repository yang diperlukan untuk menampilkan info product/seller
    @Inject lateinit var productRepo: ProductRepository
    @Inject lateinit var chatRepo: ChatRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentChatListBinding.bind(view)

        // Adapter: asumsi ChatListAdapter sekarang menerima ChatListDisplay items
        adapter = ChatListAdapter(mutableListOf()) { threadDisplay ->
            // threadDisplay.chatId should exist
            val bundle = bundleOf("chatId" to threadDisplay.chatId)
            findNavController().navigate(R.id.chatDetailFragment, bundle)
        }

        binding.rvThreads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvThreads.adapter = adapter

        // Collect threads with view lifecycle awareness to avoid NPE after onDestroyView
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.threads.collect { threads ->
                    // map each thread concurrently within the current coroutine scope
                    val list = coroutineScope {
                        val deferred = threads.map { thread ->
                            async {
                                val product = try {
                                    thread.productId?.let { pid -> productRepo.getProductOnce(pid) }
                                } catch (e: Exception) { null }

                                ChatListAdapter.ChatListDisplay(
                                    chatId = thread.chatId,
                                    productTitle = product?.title,
                                    productImage = product?.imageUrls?.firstOrNull(),
                                    sellerId = product?.ownerId,
                                    lastMessageText = thread.lastMessageText,
                                    lastMessageAt = thread.lastMessageAt
                                )
                            }
                        }
                        deferred.mapNotNull { runCatching { it.await() }.getOrNull() }
                    }

                    adapter.setItems(list)

                    // Show/hide empty state (guard binding on view lifecycle)
                    val b = _binding ?: return@collect
                    if (list.isEmpty()) {
                        b.tvEmptyChat.visibility = View.VISIBLE
                        b.rvThreads.visibility = View.GONE
                    } else {
                        b.tvEmptyChat.visibility = View.GONE
                        b.rvThreads.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
