package com.unitrade.unitrade

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val splashDelayMillis = 1200L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Delay singkat untuk menampilkan splash, lalu cek session Firebase
        Handler(Looper.getMainLooper()).postDelayed({
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                // user sudah login -> masuk ke main (nav host)
                findNavController().navigate(R.id.action_splash_to_main)
            } else {
                // belum login -> ke layar login
                findNavController().navigate(R.id.action_splash_to_login)
            }
        }, splashDelayMillis)
    }
}
