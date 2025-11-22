package com.unitrade.unitrade

import com.google.firebase.Timestamp

/**
 * app/src/main/java/com/unitrade/unitrade/data/model/ChatMessage.kt
 * Model untuk satu pesan chat.
 */
data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val text: String? = null,
    val imageUrl: String? = null,
    val type: String = "text", // "text" | "image" | "mixed"
    val createdAt: Timestamp? = null,
    val readBy: List<String> = emptyList()
)
