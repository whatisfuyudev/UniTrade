package com.unitrade.unitrade

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

// Tambah imports

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
    @Named("cloudinary_products")
    fun provideCloudinaryProducts(): CloudinaryUploader {
        val uploadPreset = "unitrade_products_preset"
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }

    @Provides
    @Singleton
    @Named("cloudinary_chat")
    fun provideCloudinaryChat(): CloudinaryUploader {
        val uploadPreset = "unitrade_chat_preset"
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }

    // NEW: profile uploader (unsigned preset with folder unitrade-profile-pictures)
    @Provides
    @Singleton
    @Named("cloudinary_profile")
    fun provideCloudinaryProfile(): CloudinaryUploader {
        val uploadPreset = "unitrade_profile_preset" // ganti sesuai preset yang kamu buat di Cloudinary
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }
}
