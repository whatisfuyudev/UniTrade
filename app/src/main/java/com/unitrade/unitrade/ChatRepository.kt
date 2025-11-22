package com.unitrade.unitrade

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.unitrade.unitrade.ChatMessage
import com.unitrade.unitrade.ChatThread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app/src/main/java/com/unitrade/unitrade/data/repository/ChatRepository.kt
 *
 * Repository yang mengelola operasi chat terhadap Firestore:
 * - create/get thread
 * - send message (text / image)
 * - observe messages realtime (Flow)
 * - list recent threads
 *
 * Catatan: gunakan batching jika butuh update multi-write (e.g., update chat doc + add message).
 */
@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    val auth: FirebaseAuth
) {
    private val chatsCol = firestore.collection("chats")

    private fun currentUid() = auth.currentUser?.uid

    /**
     * Get or create chat thread for two participants (simple 1-to-1).
     * Returns chatId.
     */
    suspend fun getOrCreateThreadWith(userA: String, userB: String): String {
        // deterministic thread id by sorting UIDs to avoid duplicates
        val participants = listOf(userA, userB).sorted()
        val threadId = participants.joinToString("_")
        val docRef = chatsCol.document(threadId)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) return threadId

        val thread = ChatThread(
            chatId = threadId,
            participants = participants,
            lastMessageText = null,
            lastMessageAt = Timestamp.now()
        )
        docRef.set(thread).await()
        return threadId
    }

    /**
     * Send message by adding document to subcollection messages and update thread metadata.
     */
    suspend fun sendMessage(chatId: String, message: ChatMessage) {
        val threadRef = chatsCol.document(chatId)
        val messagesCol = threadRef.collection("messages")
        val newDoc = messagesCol.document()
        val msgData = hashMapOf(
            "messageId" to newDoc.id,
            "senderId" to message.senderId,
            "text" to message.text,
            "imageUrl" to message.imageUrl,
            "type" to message.type,
            "createdAt" to FieldValue.serverTimestamp(),
            "readBy" to message.readBy
        )
        // Use batch to atomically write message and update thread lastMessage
        val batch = firestore.batch()
        batch.set(newDoc, msgData)
        batch.update(threadRef, mapOf(
            "lastMessageText" to (message.text ?: if (message.imageUrl != null) "Gambar" else null),
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
    }

    /**
     * Observe last N messages for a chat as Flow. Uses snapshot listener.
     * Caller collects on Main thread.
     */
    fun observeMessages(chatId: String, limit: Long = 50L) = callbackFlow<List<ChatMessage>> {
        val query = chatsCol.document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(limit)

        val registration = query.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    ChatMessage(
                        messageId = data["messageId"] as? String ?: doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        text = data["text"] as? String?,
                        imageUrl = data["imageUrl"] as? String?,
                        type = data["type"] as? String ?: "text",
                        createdAt = data["createdAt"] as? Timestamp,
                        readBy = (data["readBy"] as? List<String>) ?: emptyList()
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            trySend(list)
        }

        awaitClose { registration.remove() }
    }

    /**
     * Observe chat threads where current user is participant (list of conversations).
     */
    fun observeThreadsForUser(uid: String) = callbackFlow<List<ChatThread>> {
        val query = chatsCol.whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)

        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    ChatThread(
                        chatId = doc.id,
                        participants = (data["participants"] as? List<String>) ?: emptyList(),
                        lastMessageText = data["lastMessageText"] as? String,
                        lastMessageAt = data["lastMessageAt"] as? Timestamp,
                        unreadCounts = (data["unreadCounts"] as? Map<String, Long>) ?: emptyMap()
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }
}
