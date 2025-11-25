package com.unitrade.unitrade


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 *
 * app/src/main/java/com/unitrade/unitrade/data/repository/UserRepository.kt
 *
 * Repository untuk operasi user: ambil profile, update profile, update favorites.
 */
@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val usersCol = firestore.collection("users")

    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun getCurrentUserOnce(): UserModel? {
        val uid = currentUid() ?: return null
        val doc = usersCol.document(uid).get().await()
        return doc.toObject(UserModel::class.java)
    }

    suspend fun getUserOnce(uid: String): UserModel? {
        val doc = usersCol.document(uid).get().await()
        return doc.toObject(UserModel::class.java)
    }

    suspend fun updateProfile(uid: String, updates: Map<String, Any?>) {
        usersCol.document(uid).set(updates, SetOptions.merge()).await()
    }

    suspend fun setPhotoInfo(uid: String, photoUrl: String, publicId: String?) {
        val map = hashMapOf<String, Any?>(
            "photoUrl" to photoUrl,
            "photoPublicId" to publicId
        )
        usersCol.document(uid).set(map, SetOptions.merge()).await()
    }

    // favorites
    suspend fun addFavorite(uid: String, productId: String) {
        val ref = usersCol.document(uid)
        ref.update("favorites", com.google.firebase.firestore.FieldValue.arrayUnion(productId)).await()
    }
    suspend fun removeFavorite(uid: String, productId: String) {
        val ref = usersCol.document(uid)
        ref.update("favorites", com.google.firebase.firestore.FieldValue.arrayRemove(productId)).await()
    }
}
