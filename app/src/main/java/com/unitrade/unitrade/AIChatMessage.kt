package com.unitrade.unitrade

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class AIChatMessage(
    val messageId: String = "",
    val userId: String = "",
    val text: String = "",
    @get:PropertyName("fromUser")
    @set:PropertyName("fromUser")
    var isFromUser: Boolean = true,
    val timestamp: Timestamp = Timestamp.now()
)
