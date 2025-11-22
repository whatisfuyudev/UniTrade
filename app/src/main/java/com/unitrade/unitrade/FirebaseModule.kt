package com.unitrade.unitrade

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    // Cloudinary config - fill with your cloud name & unsigned preset
    @Provides
    @Singleton
    fun provideCloudinaryUploader(): CloudinaryUploader {
        val cloudName = "dxfrr8lsd"          // replace with your value
        val uploadPreset = "unitrade"  // replace with your unsigned preset name
        return CloudinaryUploader(cloudName, uploadPreset)
    }
}
