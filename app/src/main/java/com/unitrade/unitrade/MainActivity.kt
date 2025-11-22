/*
 File: app/src/main/java/com/unitrade/unitrade/MainActivity.kt

 Kegunaan:
 - Activity host utama yang menampung NavHostFragment.
 - Menghubungkan BottomNavigationView dengan NavController.
 - Menyembunyikan / menampilkan BottomNavigationView otomatis berdasarkan destination
   (mis. sembunyikan pada splash, onboarding, login, register).
*/

package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
    }
}
