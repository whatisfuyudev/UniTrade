//package com.unitrade.unitrade
//
//import android.os.Bundle
//import android.view.View
//import android.widget.Toast
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import androidx.navigation.fragment.findNavController
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.lifecycleScope
//import androidx.lifecycle.repeatOnLifecycle
//import com.bumptech.glide.Glide
//import com.unitrade.unitrade.R
//import com.unitrade.unitrade.UserModel
//import com.unitrade.unitrade.UserRepository
//import com.unitrade.unitrade.databinding.FragmentProfileBinding
//import com.unitrade.unitrade.AuthViewModel
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
///**
// * app/src/main/java/com/unitrade/unitrade/ui/profile/ProfileFragment.kt
// *
// * Menampilkan halaman profil pengguna:
// * - Menampilkan foto profil (Glide), displayName, email, fakultas, contact
// * - Tombol: Edit Profil, Favorit Saya, Produk Saya, Pesan/Chat, Logout
// *
// * Perubahan dibandingkan versi awal:
// * - Tidak lagi memakai ProfileViewModel yang tidak ada (menggunakan AuthViewModel untuk logout)
// * - Mengganti penggunaan deprecated `launchWhenStarted` dengan `repeatOnLifecycle`
// * - Menggunakan lifecycleScope + repeatOnLifecycle agar safe untuk coroutine & lifecycle
// *
// * Dependencies:
// * - UserRepository (injected) untuk mengambil data user (suspend function getCurrentUserOnce())
// * - AuthViewModel (existing) untuk logout()
// */
//@AndroidEntryPoint
//class ProfileFragment : Fragment(R.layout.fragment_profile) {
//
//    // view binding
//    private var _binding: FragmentProfileBinding? = null
//    private val binding get() = _binding!!
//
//    // inject user repository (dipakai untuk fetch satu kali user)
//    @Inject
//    lateinit var userRepository: UserRepository
//
//    // gunakan AuthViewModel yang sudah ada (untuk logout dll)
//    private val authViewModel: AuthViewModel by viewModels()
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        _binding = FragmentProfileBinding.bind(view)
//
//        // Load profile secara coroutine-safe
//        loadProfile()
//
//        // Navigasi ke Edit Profile (pastikan nav_graph punya action_profile_to_editProfile)
//        binding.btnEditProfile.setOnClickListener {
//            findNavController().navigate(R.id.action_profile_to_editProfile)
//        }
//
//        // Favorit saya
//        binding.btnFavorites.setOnClickListener {
//            findNavController().navigate(R.id.action_profile_to_favoritesFragment)
//        }
//
//        // Produk saya
//        binding.btnMyProducts.setOnClickListener {
//            findNavController().navigate(R.id.action_profile_to_myProductsFragment)
//        }
//
//        // Buka daftar chat
//        binding.btnChats.setOnClickListener {
//            findNavController().navigate(R.id.action_profile_to_chatListFragment)
//        }
//
//        // Logout -> gunakan fungsi yang sudah ada di AuthViewModel
//        binding.btnLogout.setOnClickListener {
//            authViewModel.logout()
//            // navigasi kembali ke login (nav_graph harus punya action_profile_to_login)
//            findNavController().navigate(R.id.action_profile_to_login)
//        }
//    }
//
//    /**
//     * loadProfile:
//     * - menggunakan viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(...) { ... } }
//     * - memanggil suspend fun userRepository.getCurrentUserOnce() (asumsi metode ini suspend)
//     */
//    private fun loadProfile() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            // repeatOnLifecycle memastikan block berjalan hanya saat lifecycle >= STARTED
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                try {
//                    val user: UserModel? = userRepository.getCurrentUserOnce()
//                    if (user == null) {
//                        Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
//                    } else {
//                        bindUser(user)
//                    }
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    Toast.makeText(requireContext(), "Error memuat profil: ${e.message}", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
//
//    private fun bindUser(user: UserModel) {
//        binding.tvDisplayName.text = user.displayName ?: "-"
//        binding.tvEmail.text = user.email ?: "-"
//        binding.tvFaculty.text = user.faculty ?: "-"
//        binding.tvContact.text = "Kontak: ${user.contact ?: "-"}"
//
//        val url = user.photoUrl?.takeIf { it != "-" && it.isNotBlank() }
//        if (!url.isNullOrBlank()) {
//            Glide.with(this)
//                .load(url)
//                .placeholder(R.drawable.placeholder)
//                .into(binding.imgAvatar)
//        } else {
//            binding.imgAvatar.setImageResource(R.drawable.placeholder)
//        }
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}
//versi lama diatas
//versi testing dibawah

package com.unitrade.unitrade

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.unitrade.unitrade.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * app/src/main/java/com/unitrade/unitrade/ProfileFragment.kt
 *
 * Menampilkan halaman profil pengguna saat ini:
 * - memuat user dari Firestore via UserRepository
 * - menampilkan foto profil (Glide), displayName, email, fakultas, contact
 * - tombol: Edit Profil, Favorit Saya, Produk Saya, Pesan/Chat, Logout
 *
 * Perubahan & debug tambahan:
 * 1) Menggunakan repeatOnLifecycle(Lifecycle.State.STARTED) untuk coroutine-safe lifecycle.
 * 2) Semua operasi suspend dipanggil menggunakan withContext(Dispatchers.IO).
 * 3) Tambahan logging verbose (Log.d / Log.w / Log.e) supaya mudah ditrace saat terjadi error Hilt / runtime.
 *
 * NOTE: Jika kamu melihat crash ClassCastException di Hilt (Dagger... cannot be cast to ProfileFragment_GeneratedInjector)
 *      lihat bagian "Troubleshooting Hilt" di bawah kode untuk langkah perbaikan.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    companion object {
        private const val TAG = "ProfileFragment"
    }

    // view binding
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // repository di-inject via Hilt
    @Inject
    lateinit var userRepository: UserRepository

    // reuse AuthViewModel yang sudah ada untuk logout
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called — fragment class: ${this::class.java.name}")
        // dump some environment info to help debugging Hilt/injection issues
        try {
            val loader = this::class.java.classLoader
            Log.d(TAG, "ClassLoader: $loader")
            Log.d(TAG, "Application package: ${requireContext().applicationContext.packageName}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read environment info: ${t.message}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated()")
        _binding = FragmentProfileBinding.bind(view)

        // setup button listeners
        binding.btnEditProfile.setOnClickListener {
            Log.d(TAG, "Navigating to EditProfile")
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }
        binding.btnFavorites.setOnClickListener {
            Log.d(TAG, "Navigating to FavoritesFragment")
            findNavController().navigate(R.id.action_profile_to_favoritesFragment)
        }
        binding.btnMyProducts.setOnClickListener {
            Log.d(TAG, "Navigating to MyProductsFragment")
            findNavController().navigate(R.id.action_profile_to_myProductsFragment)
        }
        binding.btnChats.setOnClickListener {
            Log.d(TAG, "Navigating to ChatListFragment")
            findNavController().navigate(R.id.action_profile_to_chatListFragment)
        }
        binding.btnLogout.setOnClickListener {
            Log.d(TAG, "Logout clicked")
            authViewModel.logout()
            // pastikan nav_graph punya action_profile_to_login
            try {
                findNavController().navigate(R.id.action_profile_to_login)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to login failed: ${e.message}", e)
                Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // load profile safely using repeatOnLifecycle
        loadProfile()
    }

    /**
     * loadProfile:
     * - menjalankan suspend repository call inside repeatOnLifecycle
     * - meng-log setiap langkah sehingga mudah di-debug dari logcat
     */
    private fun loadProfile() {
        Log.d(TAG, "loadProfile() — scheduling coroutine")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    Log.d(TAG, "Calling userRepository.getCurrentUserOnce() on IO dispatcher")
                    val user = withContext(Dispatchers.IO) {
                        userRepository.getCurrentUserOnce()
                    }

                    if (user == null) {
                        Log.w(TAG, "getCurrentUserOnce() returned null")
                        Toast.makeText(requireContext(), "Profil tidak ditemukan", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d(TAG, "User loaded: uid=${user.userId} displayName=${user.displayName}")
                        bindUser(user)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal memuat profil: ${e.message}", e)
                    Toast.makeText(requireContext(), "Gagal memuat profil: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindUser(user: UserModel) {
        Log.d(TAG, "bindUser() — displayName=${user.displayName} email=${user.email}")
        binding.tvDisplayName.text = user.displayName ?: "-"
        binding.tvEmail.text = user.email ?: "-"
        binding.tvFaculty.text = user.faculty ?: "-"
        binding.tvContact.text = "Kontak: ${user.contact ?: "-"}"

        val url = user.photoUrl?.takeIf { it != "-" && it.isNotBlank() }
        if (!url.isNullOrBlank()) {
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.placeholder)
                .into(binding.imgAvatar)
        } else {
            binding.imgAvatar.setImageResource(R.drawable.placeholder)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView()")
        _binding = null
    }
}


