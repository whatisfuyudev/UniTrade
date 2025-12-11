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
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UniTradeApp : Application() {
    
    @Inject
    lateinit var notificationListener: NotificationListener
    
    @Inject
    lateinit var auth: FirebaseAuth
    
    override fun onCreate() {
        super.onCreate()
        
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                notificationListener.startListening()
            } else {
                notificationListener.stopListening()
            }
        }
    }
}
