package com.unitrade.unitrade

import com.google.firebase.Timestamp

/**
 * app/src/main/java/com/unitrade/unitrade/ChatThread.kt
 *
 * Model metadata percakapan (chat thread).
 * - Ditambahkan properti productId? untuk mensupport thread per-product
 *   (mis. chat_prod_{productId}_{u1}_{u2}).
 */
data class ChatThread(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessageText: String? = null,
    val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    val productId: String? = null // <-- baru: bisa null untuk thread lama / pair-only
)
