package com.unitrade.unitrade

import com.google.firebase.Timestamp

data class AIChatSession(
    val sessionId: String = "",
    val userId: String = "",
    val lastMessageText: String = "",
    val lastMessageAt: Timestamp = Timestamp.now(),
    val messageCount: Int = 0
)
