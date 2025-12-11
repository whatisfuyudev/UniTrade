package com.unitrade.unitrade

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * app/src/main/java/com/unitrade/unitrade/FirebaseModule.kt
 *
 * Module Hilt untuk menyediakan instance Firebase dan CloudinaryUploader.
 * - Menyediakan CloudinaryUploader untuk produk, chat, profile (unsigned upload preset).
 * - Menyediakan juga API key + API secret (hardcoded) untuk operasi delete signed (TIDAK AMAN untuk produksi).
 *
 * Kamu yang minta: hardcoded credential acceptable untuk demo kampus.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    private const val CLOUD_NAME = "dxfrr8lsd"

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    @Named("cloudinary_products")
    fun provideCloudinaryProducts(): CloudinaryUploader {
        val uploadPreset = "unitrade_products_preset" // ganti sesuai preset produk di Cloudinary
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }

    @Provides
    @Singleton
    @Named("cloudinary_chat")
    fun provideCloudinaryChat(): CloudinaryUploader {
        val uploadPreset = "unitrade_chat_preset"
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }

    @Provides
    @Singleton
    @Named("cloudinary_profile")
    fun provideCloudinaryProfile(): CloudinaryUploader {
        val uploadPreset = "unitrade_profilepic_preset"
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }

    // ----------------------------------------------------------------
    // Hardcoded API key & secret for demo (client-side signed delete).
    // WARNING: storing apiSecret in client is insecure. Only for demo.
    // ----------------------------------------------------------------
    @Provides
    @Singleton
    @Named("cloudinary_api_key")
    fun provideCloudinaryApiKey(): String {
        return "965484964229326" // <-- ganti dengan api key mu untuk demo
    }

    @Provides
    @Singleton
    @Named("cloudinary_api_secret")
    fun provideCloudinaryApiSecret(): String {
        return "n1_GK9QN6m2z7X67Is7vNcxzahg" // <-- ganti dengan api secret mu untuk demo
    }
}
