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
import javax.inject.Named
import javax.inject.Inject

/**
 * app/src/main/java/com/unitrade/unitrade/ui/chat/ChatViewModel.kt
 *
 * ViewModel untuk Chat:
 * - threads: StateFlow<List<ChatThread>>
 * - sendTextMessage(chatId, text)
 * - sendImageMessage(chatId, file): UploadResult digunakan -> ambil secureUrl -> kirim message dengan imageUrl
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @Named("cloudinary_chat") private val uploader: CloudinaryUploader
) : ViewModel() {

    private val _threads = MutableStateFlow<List<com.unitrade.unitrade.ChatThread>>(emptyList())
    val threads: StateFlow<List<com.unitrade.unitrade.ChatThread>> = _threads

    init {
        val uid = repository.auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
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
     * Upload image to Cloudinary (returns UploadResult) then save message with imageUrl = result.secureUrl
     */
    suspend fun sendImageMessage(chatId: String, file: File): Boolean {
        val uid = repository.auth.currentUser?.uid ?: return false

        // UploadResult? returned by uploader.uploadImage
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
}
