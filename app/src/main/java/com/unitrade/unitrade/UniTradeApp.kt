/*
 File: app/src/main/java/com/unitrade/unitrade/UniTradeApp.kt

 Kegunaan:
 - Kelas Application untuk app UniTrade.
 - Diberi anotasi @HiltAndroidApp sehingga Hilt dapat melakukan dependency injection
   pada lifecycle application. Harus dideklarasikan di AndroidManifest.xml sebagai
   android:name=".UniTradeApp".
 - Tidak berisi logika lain; fungsinya adalah inisialisasi Hilt dan menyediakan
   konteks global aplikasi bagi komponen yang di-inject.
*/

package com.unitrade.unitrade

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UniTradeApp : Application() {
    // Jika perlu inisialisasi global lain (logging, analytics), taruh di onCreate() di sini.
}
