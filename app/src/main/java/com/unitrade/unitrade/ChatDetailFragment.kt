package com.unitrade.unitrade

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.R
import com.unitrade.unitrade.databinding.FragmentChatDetailBinding
import com.unitrade.unitrade.ChatMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * app/src/main/java/com/unitrade/unitrade/ui/chat/ChatDetailFragment.kt
 *
 * Chat detail:
 * - observes messages from ChatRepository.observeMessages(chatId)
 * - send text messages
 * - attach image: pick image from gallery, upload to Cloudinary via CloudinaryUploader, then call ChatRepository.sendMessage
 *
 * NOTE:
 * - Cloudinary unsigned preset used here for simplicity. For secure setup use signed uploads via backend.
 */
@AndroidEntryPoint
class ChatDetailFragment : Fragment(R.layout.fragment_chat_detail) {

    private var _binding: FragmentChatDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels() // implement to wrap ChatRepository and CloudinaryUploader

    private lateinit var adapter: ChatMessageAdapter
    private var chatId: String? = null

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

        // inside onViewCreated in ChatDetailFragment.kt
        adapter = ChatMessageAdapter(requireContext())
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter

        // observe messages (contoh jika viewModel.observeMessages returns Flow<List<ChatMessage>>)
        lifecycleScope.launchWhenStarted {
            viewModel.observeMessages(chatId!!).collect { list ->
                adapter.submitList(list)
                if (list.isNotEmpty()) binding.rvMessages.scrollToPosition(list.size - 1)
            }
        }


        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.sendTextMessage(chatId!!, text)
                    binding.etMessage.text?.clear()
                }
            }
        }

        binding.btnAttach.setOnClickListener {
            // pick image
            pickImageLauncher.launch("image/*")
        }
    }

    private fun handleImagePicked(uri: Uri) {
        // convert content uri to temp file
        viewLifecycleOwner.lifecycleScope.launch {
            val file = copyUriToFile(uri)
            if (file != null) {
                // show progress UI optional
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
            val temp = File.createTempFile("upload_", displayName, requireContext().cacheDir)
            temp.outputStream().use { out ->
                input.copyTo(out)
            }
            temp
        } catch (e: Exception) {
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
