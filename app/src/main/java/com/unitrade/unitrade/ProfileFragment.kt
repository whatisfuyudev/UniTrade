package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.unitrade.unitrade.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * app/src/main/java/com/unitrade/unitrade/ProfileFragment.kt
 *
 * Fragment profile sederhana untuk pengujian chat.
 * - Layout menyediakan:
 *   - tombol "Buka Daftar Chat" -> navigate ke chatListFragment
 *   - tombol "Buka Chat Contoh" -> navigate langsung ke chatDetailFragment dengan sample chatId
 *
 * Guna: mengetes fitur chat tanpa harus menavigasi dari bagian lain.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentProfileBinding.bind(view)

        // buka daftar percakapan
        binding.btnOpenChatList.setOnClickListener {
            findNavController().navigate(R.id.chatListFragment)
        }

        // buka chat detail dengan sample chatId (ubah sesuai id yang valid)
        binding.btnOpenChatDetail.setOnClickListener {
            val sampleChatId = "sample_chat_1" // ganti dengan chatId nyata saat testing
            val bundle = bundleOf("chatId" to sampleChatId)
            findNavController().navigate(R.id.chatDetailFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
