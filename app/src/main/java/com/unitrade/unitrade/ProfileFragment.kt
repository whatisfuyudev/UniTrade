package com.unitrade.unitrade

import android.os.Bundle
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
 * Catatan:
 * - Menggunakan repeatOnLifecycle(Lifecycle.State.STARTED) untuk coroutine-safe lifecycle.
 * - Semua operasi suspend dipanggil menggunakan withContext(Dispatchers.IO).
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    // view binding
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // repository di-inject via Hilt
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var auth: com.google.firebase.auth.FirebaseAuth

    // reuse AuthViewModel yang sudah ada untuk logout
    private val authViewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentProfileBinding.bind(view)

        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(requireContext(), "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.loginFragment)
            return
        }

        // setup button listeners
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }
        binding.btnFavorites.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_favoritesFragment)
        }
        binding.btnMyProducts.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_myProductsFragment)
        }
        binding.btnChats.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_chatListFragment)
        }
        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            try {
                findNavController().navigate(R.id.action_profile_to_login)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // load profile safely using repeatOnLifecycle
        loadProfile()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val user = withContext(Dispatchers.IO) {
                        userRepository.getCurrentUserOnce()
                    }

                    if (user == null) {
                        Toast.makeText(requireContext(), "Profil tidak ditemukan", Toast.LENGTH_SHORT).show()
                    } else {
                        bindUser(user)
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memuat profil: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindUser(user: UserModel) {
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
        _binding = null
    }
}
