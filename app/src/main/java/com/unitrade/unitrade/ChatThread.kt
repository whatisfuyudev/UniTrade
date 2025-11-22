package com.unitrade.unitrade

import com.google.firebase.Timestamp

/**
 * app/src/main/java/com/unitrade/unitrade/data/model/ChatThread.kt
 * Model untuk metadata percakapan (chat thread).
 */
data class ChatThread(
    val chatId: String = "",
    val participants: List<String> = emptyList(), // uid list
    val lastMessageText: String? = null,
    val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap()
)
