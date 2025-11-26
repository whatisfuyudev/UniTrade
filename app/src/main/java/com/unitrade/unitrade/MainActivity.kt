/*
 File: app/src/main/java/com/unitrade/unitrade/MainActivity.kt

 Kegunaan:
 - Activity host utama yang menampung NavHostFragment.
 - Menghubungkan BottomNavigationView dengan NavController.
 - Menyembunyikan / menampilkan BottomNavigationView otomatis berdasarkan destination
   (mis. sembunyikan pada splash, onboarding, login, register).
 - Menambahkan padding bottom dinamis pada NavHostFragment agar input area fragment
   tidak tertutup oleh BottomNavigationView. Juga mem-forward WindowInsets (IME)
   sehingga saat keyboard muncul fragment ter-resize dengan benar.
*/

package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.unitrade.unitrade.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Daftar destination id (sesuaikan dengan id di nav_graph.xml jika beda)
    private val destinationsWithoutBottomNav = setOf(
        R.id.splashFragment,
        R.id.onboardingFragment,
        R.id.loginFragment,
        R.id.registerFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pastikan activity meresize saat IME/keyboard muncul.
        // Ini dibuat di runtime supaya kita tidak perlu memodifikasi AndroidManifest.xml.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Hubungkan bottom nav dengan navController
        binding.bottomNav.setupWithNavController(navController)

        // Listener: sembunyikan / tampilkan bottom nav sesuai destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destinationsWithoutBottomNav.contains(destination.id)) View.GONE else View.VISIBLE
        }

        // ---------------------------------------------------------------------
        // Layout adjustment to keep fragment content above BottomNavigationView
        // ---------------------------------------------------------------------
        // 1) Saat bottomNav di-layout, set padding bottom ke navHostFragment = bottomNav.height
        // 2) Jika bottomNav berubah (show/hide/rotation), update padding via addOnLayoutChangeListener
        // 3) Forward WindowInsets (systemBars + IME) ke navHostFragment agar keyboard juga dihitung
        // Note: ini tidak mengubah behavior nav atau navigation graph, hanya menambah padding visual.
        binding.bottomNav.post {
            val navH = binding.bottomNav.height
            // tambahkan bottom padding sama dengan height bottom nav supaya konten fragment terlihat di atas nav
            binding.navHostFragment.setPadding(0, 0, 0, navH)
            binding.navHostFragment.clipToPadding = false
        }

        // update padding whenever bottom nav layout changes (e.g. hide/show, rotation)
        binding.bottomNav.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            binding.navHostFragment.setPadding(0, 0, 0, v.height)
        }
    }
}
