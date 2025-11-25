// File: app/src/main/java/com/unitrade/unitrade/EditProfileFragment.kt
package com.unitrade.unitrade

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.unitrade.unitrade.databinding.FragmentEditProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/**
 * app/src/main/java/com/unitrade/unitrade/EditProfileFragment.kt
 *
 * Fragment untuk edit profil:
 * - edit nama, fakultas, kontak
 * - ganti foto profil: pilih gambar -> upload ke Cloudinary (folder: unitrade-profile-pictures)
 * - jika ada foto lama (photoPublicId) maka akan dihapus via CloudinaryUploader.deleteImageSigned(...)
 *
 * Catatan:
 * - Cloudinary credentials (apiKey + apiSecret) di-inject via Hilt Named providers.
 * - deleteImageSigned dipanggil HANYA bila publicId lama tersedia.
 * - Risiko: menyimpan apiSecret di client tidak aman; hanya untuk demo internal.
 */
@AndroidEntryPoint
class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var userRepository: UserRepository

    // unsigned uploader untuk profile (upload unsigned preset)
    @Inject
    @Named("cloudinary_profile")
    lateinit var uploader: CloudinaryUploader

    // credentials untuk signed deletion (hardcoded via FirebaseModule provider)
    @Inject
    @Named("cloudinary_api_key")
    lateinit var cloudinaryApiKey: String

    @Inject
    @Named("cloudinary_api_secret")
    lateinit var cloudinaryApiSecret: String

    private var tempFileForUpload: File? = null
    private var currentUserId: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onImagePicked(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentEditProfileBinding.bind(view)

        currentUserId = userRepository.currentUid()

        // Load profile once - use repeatOnLifecycle for lifecycle-safety
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val user = userRepository.getCurrentUserOnce()
                    user?.let {
                        binding.etName.setText(it.displayName)
                        binding.etFaculty.setText(it.faculty)
                        binding.etContact.setText(it.contact)
                        val url = it.photoUrl?.takeIf { u -> u != "-" && u.isNotBlank() }
                        if (!url.isNullOrBlank()) Glide.with(this@EditProfileFragment).load(url).placeholder(R.drawable.placeholder).into(binding.imgAvatarEdit)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Gagal memuat profil: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnPickPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnSaveProfile.setOnClickListener { saveProfile() }
    }

    private fun onImagePicked(uri: Uri) {
        // preview
        Glide.with(this).load(uri).into(binding.imgAvatarEdit)

        // convert to temp file in IO thread
        viewLifecycleOwner.lifecycleScope.launch {
            // kita tidak perlu repeatOnLifecycle di sini karena ini satu-shot; cukup jalankan coroutine
            tempFileForUpload = copyUriToTempFile(uri)
            if (tempFileForUpload == null) {
                Toast.makeText(requireContext(), "Gagal membaca file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun copyUriToTempFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            val input = requireContext().contentResolver.openInputStream(uri) ?: return@withContext null
            val tmp = File.createTempFile("profile_", ".jpg", requireContext().cacheDir)
            tmp.outputStream().use { out -> input.copyTo(out) }
            tmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Simpan profile:
     *  - jika ada gambar baru: upload -> simpan secureUrl + publicId ke Firestore
     *  - jika ada foto lama (photoPublicId), coba hapus via deleteImageSigned(...)
     */
    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val faculty = binding.etFaculty.text.toString().trim()
        val contact = binding.etContact.text.toString().trim()
        val uid = currentUserId ?: run {
            Toast.makeText(requireContext(), "User tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressSaving.visibility = View.VISIBLE

        // one-shot coroutine
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // ambil dokumen user saat ini (untuk mendapatkan oldPublicId jika ada)
                val currentUserDoc = userRepository.getCurrentUserOnce()
                val oldPublicId = currentUserDoc?.photoPublicId

                // 1) upload gambar bila ada
                var newSecureUrl: String? = null
                var newPublicId: String? = null

                tempFileForUpload?.let { f ->
                    val uploadResult = uploader.uploadImage(f, folder = "unitrade-profile-pictures")
                    if (uploadResult == null) {
                        Toast.makeText(requireContext(), "Gagal upload foto", Toast.LENGTH_LONG).show()
                    } else {
                        // CloudinaryUploader.uploadImage sekarang mengembalikan UploadResult (secureUrl, publicId)
                        newSecureUrl = uploadResult.secureUrl
                        newPublicId = uploadResult.publicId
                    }
                }

                // 2) update Firestore (gabungkan fields)
                val updates = mutableMapOf<String, Any?>()
                updates["displayName"] = name
                updates["faculty"] = faculty
                updates["contact"] = contact

                newSecureUrl?.let {
                    updates["photoUrl"] = it
                    updates["photoPublicId"] = newPublicId
                }

                // lakukan update profile (suspend)
                userRepository.updateProfile(uid, updates)

                // 3) jika ada foto lama dan kita berhasil upload foto baru -> hapus foto lama via signed API
                //    only attempt delete if oldPublicId exists and berbeda dari newPublicId
                if (!oldPublicId.isNullOrBlank() && !newPublicId.isNullOrBlank() && oldPublicId != newPublicId) {
                    try {
                        val deleted = uploader.deleteImageSigned(oldPublicId, cloudinaryApiKey, cloudinaryApiSecret)
                        if (!deleted) {
                            // hapus gagal, log / beri informasi ringan (tidak fatal)
                            // jangan gagalkan update profile hanya karena delete gagal
                            Toast.makeText(requireContext(), "Foto lama gagal dihapus (tidak fatal)", Toast.LENGTH_SHORT).show()
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        // don't block user; hanya beri warning
                        Toast.makeText(requireContext(), "Error saat menghapus foto lama: ${ex.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                Toast.makeText(requireContext(), "Profil diperbarui", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressSaving.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
