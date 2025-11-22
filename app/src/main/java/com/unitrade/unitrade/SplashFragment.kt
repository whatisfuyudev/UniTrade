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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    // DI injection dari Hilt: manager untuk status onboarding
    @Inject
    lateinit var onboardingManager: OnboardingManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Gunakan lifecycleScope untuk menjalankan suspend read dari DataStore
        lifecycleScope.launch {
            // Ambil satu value saat ini. first() mengambil value pertama dan membatalkan koleksi.
            val isFirstLaunch = onboardingManager.isFirstLaunchFlow.first()

            // Navigasi berdasarkan flag
            if (isFirstLaunch) {
                // Jika pertama kali, buka onboarding
                findNavController().navigate(R.id.action_splash_to_onboarding)
            } else {
                // Bukan pertama kali, langsung ke main (home)
                findNavController().navigate(R.id.action_splash_to_main)
            }
        }
    }
}
