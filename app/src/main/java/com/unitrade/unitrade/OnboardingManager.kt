package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/data/local/OnboardingManager.kt

 Kegunaan:
 - Wrapper / helper untuk mengakses flag onboarding di DataStore Preferences.
 - Menyediakan Flow<Boolean> isFirstLaunchFlow yang meng-emits true bila app
   belum pernah menjalankan onboarding (default true).
 - Menyediakan fungsi suspend setFirstLaunchCompleted() untuk menyet flag agar
   onboarding tidak muncul lagi.
 - File ini dipanggil dari SplashFragment untuk menentukan navigasi awal,
   dan dari OnboardingFragment untuk menandai onboarding selesai.
*/

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OnboardingManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // Kunci preferensi untuk flag first launch
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("prefs_first_launch")
    }

    /**
     * Flow yang meng-emits apakah ini pertama kali membuka app.
     * Default: true artinya anggap pertama kali bila key belum ada.
     */
    val isFirstLaunchFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_FIRST_LAUNCH] ?: true }

    /**
     * Set flag menjadi false setelah onboarding selesai.
     * Dipanggil dari OnboardingFragment saat user menekan tombol "Mulai".
     */
    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = false
        }
    }

    /**
     * Optional helper untuk testing: kembalikan flag ke true.
     */
    suspend fun resetFirstLaunch() {
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = true
        }
    }
}

