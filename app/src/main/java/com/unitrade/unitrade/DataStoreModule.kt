package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/di/DataStoreModule.kt

 Kegunaan:
 - Modul Hilt yang menyediakan instance DataStore<Preferences> secara singleton.
 - DataStore akan dipakai untuk menyimpan preferensi seperti flag "first launch"
   untuk onboarding, dan shared preferences ringan lainnya.
 - Dengan menyediakan lewat DI, semua class yang butuh DataStore dapat di-inject.
*/

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Menyediakan DataStore<Preferences> singleton.
     * File yang dibuat: "unitrade_prefs" di direktori file internal app.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext appContext: Context) =
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("unitrade_prefs") }
        )
}
