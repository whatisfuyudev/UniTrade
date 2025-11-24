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

    // uploader untuk produk (preset unsigned yang asset folder-nya
    // diset ke unitrade-products-pictures atau tanpa asset folder)
    @Provides
    @Singleton
    @Named("cloudinary_products")
    fun provideCloudinaryProducts(): CloudinaryUploader {
        val uploadPreset = "unitrade_products_preset" // ganti sesuai nama preset produk di console
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }

    // uploader untuk chat (preset unsigned yang asset folder-nya
    // diset ke unitrade-chat-pictures atau tanpa asset folder)
    @Provides
    @Singleton
    @Named("cloudinary_chat")
    fun provideCloudinaryChat(): CloudinaryUploader {
        val uploadPreset = "unitrade_chat_preset" // ganti sesuai nama preset chat di console
        return CloudinaryUploader(CLOUD_NAME, uploadPreset)
    }
}
