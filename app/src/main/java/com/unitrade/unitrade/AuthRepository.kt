package com.unitrade.unitrade

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun register(email: String, password: String, displayName: String, extra: Map<String, Any>? = null): Result<FirebaseUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("User null")
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            user.updateProfile(profileUpdates).await()

            // create user doc in Firestore aligned with UserModel
            val userDoc = hashMapOf<String, Any?>(
                "userId" to user.uid,
                "email" to email,
                "displayName" to displayName,
                "faculty" to "",
                "role" to "normal",
                "contact" to null,
                "photoUrl" to null,
                "photoPublicId" to null,
                "favorites" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            // allow extras (e.g., provided faculty) to override defaults
            extra?.let { userDoc.putAll(it) }
            firestore.collection("users").document(user.uid).set(userDoc).await()

            // send email verification
            user.sendEmailVerification().await()

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("User null")
            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    fun logout() {
        auth.signOut()
    }
}
