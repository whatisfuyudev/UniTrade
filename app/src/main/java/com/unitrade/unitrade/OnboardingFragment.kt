package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/ui/onboarding/OnboardingFragment.kt
 Kegunaan:
 - Mengontrol ViewPager2 halaman onboarding, meng-update title/description pada card,
 - Meng-handle tombol Next/Back/Skip dan men-set flag onboarding selesai (DataStore).
 - Setelah selesai, navigasi ke main_nav. Bottom nav akan disembunyikan oleh MainActivity listener.
*/

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.unitrade.unitrade.R
import com.unitrade.unitrade.OnboardingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    @Inject lateinit var onboardingManager: OnboardingManager

    private lateinit var vp: ViewPager2
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var tabDots: TabLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // bind views
        vp = view.findViewById(R.id.vpOnboarding)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvDescription = view.findViewById(R.id.tvDescription)
        btnNext = view.findViewById(R.id.btnNext)
        btnBack = view.findViewById(R.id.btnBack)
        tabDots = view.findViewById(R.id.tabDots)
        val tvSkip = view.findViewById<TextView>(R.id.tvSkip)

        // pages data (ganti drawable dengan asetmu)
        val pages = listOf(
            OnboardingPage(R.drawable.onboarding1, "Dapatkan inspirasi", "Temukan barang bekas berkualitas dari teman kampus."),
            OnboardingPage(R.drawable.onboarding2, "Taruh rencanamu", "Tambah listing produk dengan mudah dan cepat."),
            OnboardingPage(R.drawable.onboarding3, "Nikmati waktu Anda", "Tukar pesan dengan penjual dan selesaikan transaksi offline.")
        )

        val adapter = OnboardingAdapter(pages)
        vp.adapter = adapter

        // setelah vp.adapter = adapter
        val tabLayout = tabDots
        val vp = view.findViewById<ViewPager2>(R.id.vpOnboarding)

// pastikan adapter sudah ada dan itemCount > 0
        TabLayoutMediator(tabLayout, vp) { tab, position ->
            // set icon selector untuk setiap tab
            tab.setIcon(R.drawable.dot_selector)
        }.attach()

        // update title/desc for current page
        fun updateCardFor(position: Int) {
            tvTitle.text = pages[position].title
            tvDescription.text = pages[position].description
            btnBack.visibility = if (position == 0) View.GONE else View.VISIBLE
            btnNext.text = if (position == adapter.itemCount - 1) getString(R.string.get_started) else getString(R.string.next)
        }

        // initial
        updateCardFor(0)

        // page change listener
        vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCardFor(position)
            }
        })

        // Next button
        btnNext.setOnClickListener {
            val cur = vp.currentItem
            if (cur < adapter.itemCount - 1) {
                vp.currentItem = cur + 1
            } else {
                // terakhir: set flag dan navigasi ke main (home)
                lifecycleScope.launch {
                    onboardingManager.setFirstLaunchCompleted()
                    findNavController().navigate(R.id.action_onboarding_to_main)
                }
            }
        }

        // Back button
        btnBack.setOnClickListener {
            val cur = vp.currentItem
            if (cur > 0) vp.currentItem = cur - 1
        }

        // Skip button
        tvSkip.setOnClickListener {
            lifecycleScope.launch {
                onboardingManager.setFirstLaunchCompleted()
                findNavController().navigate(R.id.action_onboarding_to_main)
            }
        }
    }
}
