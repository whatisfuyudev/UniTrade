package com.unitrade.unitrade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service untuk mendengarkan pending notifications dari Firestore
 * dan menampilkan notifikasi lokal
 */
@Singleton
class NotificationListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenerRegistration: ListenerRegistration? = null
    private var currentToken: String? = null

    fun startListening() {
        stopListening()
        
        scope.launch {
            try {
                val token = messaging.token.await()
                currentToken = token
                
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    firestore.collection("users")
                        .document(userId)
                        .update("fcmToken", token)
                        .await()
                }
                
                listenToNotifications(token)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenToNotifications(token: String) {
        listenerRegistration = firestore.collection("pending_notifications")
            .whereEqualTo("token", token)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val title = doc.getString("title") ?: "UniTrade"
                        val body = doc.getString("body") ?: ""
                        val productId = doc.getString("productId")
                        val chatId = doc.getString("chatId")
                        val type = doc.getString("type") ?: "product"

                        showNotification(title, body, type, productId, chatId)

                        scope.launch {
                            try {
                                doc.reference.update("read", true).await()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    private fun showNotification(title: String, body: String, type: String, productId: String?, chatId: String?) {
        val channelId = when (type) {
            "chat" -> "unitrade_chat_channel"
            else -> "unitrade_channel"
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val productChannel = NotificationChannel(
                "unitrade_channel",
                "Produk & Favorit",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi untuk produk favorit yang terjual"
            }
            notificationManager.createNotificationChannel(productChannel)
            
            val chatChannel = NotificationChannel(
                "unitrade_chat_channel",
                "Pesan Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi untuk pesan chat baru"
            }
            notificationManager.createNotificationChannel(chatChannel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            when (type) {
                "chat" -> chatId?.let { putExtra("chatId", it) }
                else -> productId?.let { putExtra("productId", it) }
            }
            putExtra("notificationType", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_unitrade_oren)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
