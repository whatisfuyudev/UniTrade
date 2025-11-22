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

/**
 * app/src/main/java/com/unitrade/unitrade/ui/chat/ChatViewModel.kt
 *
 * ViewModel untuk Chat UI:
 * - expose threads: StateFlow<List<ChatThread>> sehingga Fragment dapat meng-collectnya.
 * - gunakan ChatRepository.observeThreadsForUser(uid) untuk mengisi threads.
 * - menyediakan fungsi untuk mengirim teks dan gambar.
 *
 * Pastikan ChatRepository memiliki method:
 *   fun observeThreadsForUser(uid: String): Flow<List<ChatThread>>
 * dan bahwa ChatThread model berada di package yang sesuai.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val uploader: CloudinaryUploader
) : ViewModel() {

    // threads yang dapat di-observe oleh UI
    private val _threads = MutableStateFlow<List<com.unitrade.unitrade.ChatThread>>(emptyList())
    val threads: StateFlow<List<com.unitrade.unitrade.ChatThread>> = _threads

    init {
        // mulai collect threads bila user sudah login
        val uid = repository.auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                // repository.observeThreadsForUser(uid) harus mengembalikan Flow<List<ChatThread>>
                repository.observeThreadsForUser(uid).collect { list ->
                    _threads.value = list
                }
            }
        }
    }

    fun observeMessages(chatId: String) = repository.observeMessages(chatId)

    suspend fun sendTextMessage(chatId: String, text: String) {
        val uid = repository.auth.currentUser?.uid ?: return
        val msg = ChatMessage(
            messageId = "",
            senderId = uid,
            text = text,
            type = "text",
            createdAt = null
        )
        repository.sendMessage(chatId, msg)
    }

    /**
     * Upload image to Cloudinary then save message with imageUrl
     */
    suspend fun sendImageMessage(chatId: String, file: File): Boolean {
        val uid = repository.auth.currentUser?.uid ?: return false
        val url = uploader.uploadImage(file, folder = "unitrade-chat-pictures") ?: return false
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
}
