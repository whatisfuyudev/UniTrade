package com.unitrade.unitrade

import com.google.firebase.Timestamp

/**
 * app/src/main/java/com/unitrade/unitrade/data/model/UserModel.kt
 *
 * Model user yang mencerminkan struktur dokumen di Firestore (collection "users").
 */
data class UserModel(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val faculty: String = "",
    val role: String = "normal",
    val contact: String? = null,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,  // optional: simpan public_id dari Cloudinary
    val favorites: List<String> = emptyList(),
    val fcmToken: String? = null,
    val createdAt: Timestamp? = null
)
