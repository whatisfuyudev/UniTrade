package com.unitrade.unitrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unitrade.unitrade.ChatMessage
import com.unitrade.unitrade.ChatRepository
import com.unitrade.unitrade.CloudinaryUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/**
 * app/src/main/java/com/unitrade/unitrade/ChatViewModel.kt
 *
 * ViewModel untuk Chat:
 * - Menyediakan StateFlow<List<ChatThread>> (threads) yang di-update dari repository.
 * - Melakukan client-side sorting threads berdasarkan lastMessageAt (descending).
 * - Menyediakan observeMessages(chatId) yang meneruskan dari repository.
 * - Menyediakan sendTextMessage(chatId, text) untuk mengirim pesan teks.
 * - Menyediakan sendImageMessage(chatId, file) yang:
 *     1) mengunggah file ke Cloudinary menggunakan CloudinaryUploader dengan folder "unitrade-chat-pictures"
 *     2) membuat pesan dengan imageUrl = result.secureUrl lalu memanggil repository.sendMessage
 * - Menyediakan getOrCreateThread(userA, userB, productId?) yang memanggil repository.getOrCreateThreadWith(...)
 *
 * Catatan:
 * - File ini menggunakan Hilt (@HiltViewModel) untuk inject ChatRepository & CloudinaryUploader.
 * - Lokasi file tetap: app/src/main/java/com/unitrade/unitrade/ChatViewModel.kt
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @Named("cloudinary_chat") private val uploader: CloudinaryUploader
) : ViewModel() {

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads

    init {
        val uid = repository.auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                // observe threads then sort client-side by lastMessageAt desc to avoid Firestore composite index requirement
                repository.observeThreadsForUser(uid).collect { list ->
                    _threads.value = list.sortedByDescending { it.lastMessageAt?.toDate()?.time ?: 0L }
                }
            }
        }
    }

    /**
     * Observes messages for a chat (forward to repository).
     */
    fun observeMessages(chatId: String) = repository.observeMessages(chatId)

    /**
     * Send plain text message.
     */
    suspend fun sendTextMessage(chatId: String, text: String) {
        val uid = repository.auth.currentUser?.uid ?: return
        val msg = ChatMessage(
            messageId = "",
            senderId = uid,
            text = text,
            imageUrl = null,
            type = "text",
            createdAt = null
        )
        repository.sendMessage(chatId, msg)
    }

    /**
     * Upload image to Cloudinary under "unitrade-chat-pictures" folder then send message with imageUrl.
     * Returns true on success, false on failure.
     */
    suspend fun sendImageMessage(chatId: String, file: File): Boolean {
        val uid = repository.auth.currentUser?.uid ?: return false
        val result = uploader.uploadImage(file, folder = "unitrade-chat-pictures") ?: return false
        val url = result.secureUrl
        val msg = ChatMessage(
            messageId = "",
            senderId = uid,
            text = null,
            imageUrl = url,
            type = "image",
            createdAt = null
        )
        repository.sendMessage(chatId, msg)
        return true
    }

    /**
     * Convenience helper: create/get deterministic thread (optionally product-scoped).
     * Delegates to repository.getOrCreateThreadWith(...)
     */
    suspend fun getOrCreateThread(userA: String, userB: String, productId: String? = null): String {
        return repository.getOrCreateThreadWith(userA, userB, productId)
    }
}
