package com.unitrade.unitrade

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIChatbotRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val apiKey = "VALID_GOOGLE_AI_API_KEY"
    
    private val generativeModel = GenerativeModel(
        modelName = "models/gemini-2.5-flash",
        apiKey = apiKey
    )

    // FAQ context for the AI model
    private val faqContext = """
        You are a helpful FAQ assistant for UniTrade, a marketplace app for university students.
        
        UniTrade allows students to:
        - Buy and sell products within their university community
        - Chat with other students about products
        - Save favorite items
        - Manage their own product listings
        - Browse products by category and search
        
        Common questions and answers:
        
        Q: What is UniTrade?
        A: UniTrade is a marketplace platform designed specifically for university students to buy and sell items within their campus community safely and conveniently.
        
        Q: How do I create an account?
        A: You can register by providing your email, name, and password on the registration screen. Make sure to use your university email for verification.
        
        Q: How do I list a product?
        A: Tap the "+" button on the home screen, fill in the product details including title, description, price, category, and upload photos. Then submit your listing.
        
        Q: How do I contact a seller?
        A: On any product detail page, tap the chat button to start a conversation with the seller directly.
        
        Q: How do I save items I'm interested in?
        A: Tap the heart icon on any product to add it to your favorites. You can view all favorites from your profile.
        
        Q: How do I edit or delete my listings?
        A: Go to "My Products" from your profile, then tap on the product you want to edit or delete.
        
        Q: Is payment handled in the app?
        A: Currently, payment arrangements are made directly between buyers and sellers. We recommend meeting in safe, public locations on campus.
        
        Q: How do I change my profile information?
        A: Go to your profile and tap "Edit Profile" to update your name, photo, and other details.
        
        Q: What categories are available?
        A: We offer various categories including Electronics, Books, Clothing, Furniture, and more to help you find what you need.
        
        Please answer user questions based on this information. Be helpful, concise, and friendly.
        If a question is not related to UniTrade, politely redirect the user back to app-related topics.
    """.trimIndent()

    suspend fun getOrCreateChatSession(): Result<AIChatSession> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.Error(Exception("User not logged in"))
            
            val sessionDoc = firestore.collection("aiChatSessions")
                .document(userId)
                .get()
                .await()

            val session = if (sessionDoc.exists()) {
                sessionDoc.toObject(AIChatSession::class.java) ?: AIChatSession(
                    sessionId = userId,
                    userId = userId
                )
            } else {
                val newSession = AIChatSession(
                    sessionId = userId,
                    userId = userId
                )
                firestore.collection("aiChatSessions")
                    .document(userId)
                    .set(newSession)
                    .await()
                newSession
            }

            Result.Success(session)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun loadChatHistory(): Result<List<AIChatMessage>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.Error(Exception("User not logged in"))

            val messages = firestore.collection("aiChatSessions")
                .document(userId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(AIChatMessage::class.java) }

            Result.Success(messages)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun sendMessage(userMessage: String): Result<AIChatMessage> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.Error(Exception("User not logged in"))

            // Save user message to Firebase
            val userMsgId = firestore.collection("aiChatSessions")
                .document(userId)
                .collection("messages")
                .document().id

            val userChatMessage = AIChatMessage(
                messageId = userMsgId,
                userId = userId,
                text = userMessage,
                isFromUser = true,
                timestamp = Timestamp.now()
            )

            firestore.collection("aiChatSessions")
                .document(userId)
                .collection("messages")
                .document(userMsgId)
                .set(userChatMessage)
                .await()

            // Get AI response
            val chat = generativeModel.startChat(
                history = listOf(
                    content(role = "user") { text(faqContext) },
                    content(role = "model") { text("I understand. I'm ready to help users with questions about UniTrade.") }
                )
            )

            val response = chat.sendMessage(userMessage)
            val aiResponseText = response.text ?: "I'm sorry, I couldn't generate a response."

            // Save AI response to Firebase
            val aiMsgId = firestore.collection("aiChatSessions")
                .document(userId)
                .collection("messages")
                .document().id

            val aiChatMessage = AIChatMessage(
                messageId = aiMsgId,
                userId = userId,
                text = aiResponseText,
                isFromUser = false,
                timestamp = Timestamp.now()
            )

            firestore.collection("aiChatSessions")
                .document(userId)
                .collection("messages")
                .document(aiMsgId)
                .set(aiChatMessage)
                .await()

            // Update or create session metadata
            val sessionRef = firestore.collection("aiChatSessions").document(userId)
            val sessionDoc = sessionRef.get().await()
            
            if (sessionDoc.exists()) {
                // Update existing session
                sessionRef.update(
                    mapOf(
                        "lastMessageText" to aiResponseText,
                        "lastMessageAt" to Timestamp.now(),
                        "messageCount" to com.google.firebase.firestore.FieldValue.increment(2)
                    )
                ).await()
            } else {
                // Create new session
                val newSession = AIChatSession(
                    sessionId = userId,
                    userId = userId,
                    lastMessageText = aiResponseText,
                    lastMessageAt = Timestamp.now(),
                    messageCount = 2
                )
                sessionRef.set(newSession).await()
            }

            Result.Success(aiChatMessage)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun clearChatHistory(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.Error(Exception("User not logged in"))

            val messages = firestore.collection("aiChatSessions")
                .document(userId)
                .collection("messages")
                .get()
                .await()

            val batch = firestore.batch()
            messages.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            // Reset or create session metadata
            val sessionRef = firestore.collection("aiChatSessions").document(userId)
            val sessionDoc = sessionRef.get().await()
            
            if (sessionDoc.exists()) {
                // Update existing session
                sessionRef.update(
                    mapOf(
                        "lastMessageText" to "",
                        "messageCount" to 0
                    )
                ).await()
            } else {
                // Create new session if it doesn't exist
                val newSession = AIChatSession(
                    sessionId = userId,
                    userId = userId,
                    lastMessageText = "",
                    messageCount = 0
                )
                sessionRef.set(newSession).await()
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
