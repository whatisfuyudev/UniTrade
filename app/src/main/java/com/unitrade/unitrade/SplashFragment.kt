package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/ui/splash/SplashFragment.kt

 Kegunaan:
 - Fragment splash yang tampil pertama saat app dibuka (startDestination di nav_graph).
 - Tanggung jawab:
   1) Membaca flag isFirstLaunch dari OnboardingManager untuk menentukan alur:
      - jika true -> navigasi ke OnboardingFragment
      - jika false -> navigasi ke main_nav (Home)
   2) Dapat juga menjalankan inisialisasi awal singkat bila perlu.
 - Implementasi memakai coroutine lifecycleScope untuk membaca Flow (first()).
 - Fragment ini tidak menampilkan logika UI kompleks; cukup pengecekan dan navigasi.
*/

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.unitrade.unitrade.R
import com.unitrade.unitrade.OnboardingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    @Inject
    lateinit var onboardingManager: OnboardingManager

    // minimal durasi splash dalam millisecond (1500 ms = 1.5 detik)
    private val minSplashMillis = 1500L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val start = System.currentTimeMillis()

            // baca flag onboarding (suspend). jika error fallback ke true
            val isFirstLaunch = try {
                onboardingManager.isFirstLaunchFlow.first()
            } catch (e: Exception) {
                true
            }

            // pastikan splash tampil minimal minSplashMillis
            val elapsed = System.currentTimeMillis() - start
            val remaining = minSplashMillis - elapsed
            if (remaining > 0) {
                delay(remaining)
            }

            // navigasi berdasarkan flag
            if (isFirstLaunch) {
                findNavController().navigate(R.id.action_splash_to_onboarding)
            } else {
                findNavController().navigate(R.id.action_splash_to_main)
            }
        }
    }
}
