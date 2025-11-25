package com.unitrade.unitrade

import com.google.firebase.Timestamp

data class AIChatMessage(
    val messageId: String = "",
    val userId: String = "",
    val text: String = "",
    val isFromUser: Boolean = true,
    val timestamp: Timestamp = Timestamp.now()
)
