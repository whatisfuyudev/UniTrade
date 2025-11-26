package com.unitrade.unitrade

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.unitrade.unitrade.databinding.FragmentChatDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * app/src/main/java/com/unitrade/unitrade/ChatDetailFragment.kt
 *
 * ChatDetailFragment (fixed):
 * - Removed kotlin-reflect usage which caused runtime crash when kotlin-reflect is not present.
 * - Uses direct is-check + cast for ChatMessageAdapter, then ListAdapter submitList, then
 *   safe Java reflection fallback (submitMessageList or submitList) and final fallback
 *   to mutate an 'items' field if present.
 * - Keeps window inset handling so inputContainer stays above bottom nav / IME.
 *
 * Pastikan layout `fragment_chat_detail.xml` memiliki view dengan id `inputContainer`.
 */
@AndroidEntryPoint
class ChatDetailFragment : Fragment(R.layout.fragment_chat_detail) {

    private var _binding: FragmentChatDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    
    @Inject
    lateinit var userRepository: UserRepository

    // adapter typed as our ChatMessageAdapter for normal case
    private lateinit var adapter: ChatMessageAdapter
    private var chatId: String? = null

    private val TAG = "ChatDetailFragment"

    // image picker
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImagePicked(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentChatDetailBinding.bind(view)

        // get chatId from args
        chatId = arguments?.getString("chatId")
        if (chatId == null) {
            Toast.makeText(requireContext(), "chatId tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        // Initialize adapter and attach immediately
        adapter = ChatMessageAdapter(requireContext(), userRepository)
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter

        // Observe messages. collectLatest cancels previous handler if new emission arrives quickly
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.observeMessages(chatId!!).collectLatest { list ->
                    Log.d(TAG, "messages received: size=${list.size}")

                    // Preferred path: adapter is our ChatMessageAdapter which exposes submitMessageList(...)
                    try {
                        if (adapter is ChatMessageAdapter) {
                            (adapter as ChatMessageAdapter).submitMessageList(list)
                        } else if (adapter is ListAdapter<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            (adapter as ListAdapter<Any, androidx.recyclerview.widget.RecyclerView.ViewHolder>)
                                .submitList(list as List<Any>)
                        } else {
                            // Java reflection fallback: try submitMessageList(List) then submitList(List)
                            var invoked = false
                            try {
                                val m = adapter.javaClass.getMethod("submitMessageList", List::class.java)
                                m.invoke(adapter, list)
                                invoked = true
                            } catch (e: NoSuchMethodException) {
                                // ignore, try next
                            }

                            if (!invoked) {
                                try {
                                    val m2 = adapter.javaClass.getMethod("submitList", List::class.java)
                                    m2.invoke(adapter, list)
                                    invoked = true
                                } catch (e: NoSuchMethodException) {
                                    // ignore, final fallback below
                                }
                            }

                            if (!invoked) {
                                // Final fallback: mutate an 'items' MutableList field if adapter uses it
                                try {
                                    val field = adapter.javaClass.getDeclaredField("items")
                                    field.isAccessible = true
                                    val itemsRef = field.get(adapter) as? MutableList<Any>
                                    itemsRef?.let {
                                        it.clear()
                                        it.addAll(list as Collection<Any>)
                                        adapter.notifyDataSetChanged()
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "No known method to update adapter and fallback failed: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating adapter: ${e.message}", e)
                    }

                    // scroll to bottom after update
                    val lastIndex = try { adapter.itemCount - 1 } catch (_: Exception) { -1 }
                    if (lastIndex >= 0) binding.rvMessages.scrollToPosition(lastIndex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting messages flow: ${e.message}", e)
            }
        }

        // send text
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.sendTextMessage(chatId!!, text)
                    binding.etMessage.text?.clear()
                }
            }
        }

        // attach image
        binding.btnAttach.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

    }

    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun handleImagePicked(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val file = copyUriToFile(uri)
            if (file != null) {
                val success = viewModel.sendImageMessage(chatId!!, file)
                if (!success) {
                    Toast.makeText(requireContext(), "Upload gambar gagal", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Gagal membaca file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun copyUriToFile(uri: Uri): File? {
        return try {
            val input = requireContext().contentResolver.openInputStream(uri) ?: return null
            val displayName = queryFileName(uri) ?: "upload_${System.currentTimeMillis()}.jpg"
            val sanitized = displayName.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
            val temp = File.createTempFile("upload_", "_$sanitized", requireContext().cacheDir)
            temp.outputStream().use { out -> input.copyTo(out) }
            temp
        } catch (e: Exception) {
            Log.w(TAG, "copyUriToFile error: ${e.message}")
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
