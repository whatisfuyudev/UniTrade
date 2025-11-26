package com.unitrade.unitrade

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app/src/main/java/com/unitrade/unitrade/ChatRepository.kt
 *
 * Repository yang mengelola operasi chat terhadap Firestore:
 * - Buat/ambil thread (baik pasangan umum maupun deterministik per-product)
 * - Kirim pesan (text/image) dan update metadata thread (batch)
 * - Observe messages realtime (callbackFlow)
 * - Observe threads untuk user (callbackFlow) — sekarang WITHOUT orderBy to avoid composite index requirement
 *
 * Catatan:
 * - getOrCreateThreadWith(userA,userB) (lama) masih dipertahankan dan memanggil overload baru tanpa productId.
 * - Jika kamu ingin menampilkan threads terurut berdasarkan lastMessageAt, lakukan sorting di client (ViewModel) setelah menerima daftar.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    val auth: FirebaseAuth
) {
    private val chatsCol = firestore.collection("chats")
    private fun currentUid() = auth.currentUser?.uid

    /**
     * (Keberadaan lama) Tetap sediakan fungsi lama untuk kompatibilitas.
     * Memanggil versi yang menerima productId = null.
     */
    suspend fun getOrCreateThreadWith(userA: String, userB: String): String {
        return getOrCreateThreadWith(userA, userB, null)
    }

    /**
     * New/updated:
     * Buat atau ambil thread secara deterministik.
     * Jika productId != null -> buat thread khusus untuk pasangan user + product.
     * Mengembalikan chatId (document id).
     *
     * Penjelasan id deterministik:
     * - Mengurutkan UID sehingga id konsisten: "chat_prod_{productId}_{minUid}_{maxUid}"
     * - Keuntungan: tidak perlu melakukan query kompleks untuk menemukan thread.
     */
    suspend fun getOrCreateThreadWith(userA: String, userB: String, productId: String? = null): String {
        // deterministic ordering so id is consistent
        val (u1, u2) = listOf(userA, userB).sorted()
        val docId = if (!productId.isNullOrBlank()) {
            "chat_prod_${productId}_${u1}_$u2"
        } else {
            "chat_${u1}_$u2"
        }

        val docRef = chatsCol.document(docId)
        val snap = docRef.get().await()
        if (snap.exists()) return docId

        val data = mutableMapOf<String, Any?>(
            "chatId" to docId,
            "participants" to listOf(userA, userB),
            "lastMessageText" to null,
            "lastMessageAt" to FieldValue.serverTimestamp()
        )
        if (!productId.isNullOrBlank()) data["productId"] = productId

        docRef.set(data).await()
        return docId
    }

    /**
     * Send message by adding document to subcollection messages and update thread metadata.
     * (Tidak dihapus, behaviour tetap sama.)
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
     * (Sama seperti sebelumnya, tetap menggunakan orderBy pada messages)
     */
    fun observeMessages(chatId: String, limit: Long = 100L) = callbackFlow<List<ChatMessage>> {
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
     * Observe chat threads where current user is participant.
     * IMPORTANT: removed `orderBy("lastMessageAt", DESCENDING)` here to avoid composite index requirement.
     * Sorting should be done client-side after receiving the list (ViewModel).
     */
    fun observeThreadsForUser(uid: String) = callbackFlow<List<ChatThread>> {
        val query = chatsCol.whereArrayContains("participants", uid)
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
                        productId = data["productId"] as? String
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    /**
     * Convenience: get thread document snapshot once (suspend).
     * Useful untuk mengambil productId / metadata thread.
     */
    suspend fun getThreadOnce(chatId: String): DocumentSnapshot {
        return chatsCol.document(chatId).get().await()
    }
}
