package com.unitrade.unitrade

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIChatbotViewModel @Inject constructor(
    private val repository: AIChatbotRepository
) : ViewModel() {

    private val _messages = MutableLiveData<List<AIChatMessage>>()
    val messages: LiveData<List<AIChatMessage>> = _messages

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadChatHistory()
    }

    fun loadChatHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.loadChatHistory()) {
                is Result.Success -> {
                    _messages.value = result.data
                    _error.value = null
                }
                is Result.Error -> {
                    _error.value = result.exception.message
                }
                is Result.Loading -> {
                    // Already handled by _isLoading
                }
            }
            _isLoading.value = false
        }
    }

    fun sendMessage(messageText: String) {
        if (messageText.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            
            // Add user message to UI immediately
            val currentMessages = _messages.value.orEmpty().toMutableList()
            val tempUserMessage = AIChatMessage(
                messageId = "temp_${System.currentTimeMillis()}",
                userId = "",
                text = messageText,
                isFromUser = true,
                timestamp = com.google.firebase.Timestamp.now()
            )
            currentMessages.add(tempUserMessage)
            _messages.value = currentMessages

            when (val result = repository.sendMessage(messageText)) {
                is Result.Success -> {
                    // Reload to get actual messages with IDs from Firebase
                    loadChatHistory()
                    _error.value = null
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to send message"
                    // Remove temp message on error
                    currentMessages.remove(tempUserMessage)
                    _messages.value = currentMessages
                }
                is Result.Loading -> {
                    // Already handled by _isLoading
                }
            }
            
            _isLoading.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.clearChatHistory()) {
                is Result.Success -> {
                    _messages.value = emptyList()
                    _error.value = null
                }
                is Result.Error -> {
                    _error.value = result.exception.message
                }
                is Result.Loading -> {
                    // Already handled by _isLoading
                }
            }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
