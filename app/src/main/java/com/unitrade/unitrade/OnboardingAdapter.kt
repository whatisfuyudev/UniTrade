package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/ui/onboarding/OnboardingAdapter.kt

 Kegunaan:
 - Adapter untuk ViewPager2 yang menampilkan halaman onboarding.
 - Setiap halaman diisi oleh model OnboardingPage (gambar, judul, deskripsi).
 - Simple RecyclerView.Adapter berbasis ViewPager2.
*/

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.unitrade.unitrade.R

data class OnboardingPage(
    val imageRes: Int,   // drawable resource id
    val title: String,
    val description: String
)

class OnboardingAdapter(private val pages: List<OnboardingPage>) :
    RecyclerView.Adapter<OnboardingAdapter.PageVH>() {

    inner class PageVH(view: View) : RecyclerView.ViewHolder(view) {
        val img = view.findViewById<ImageView>(R.id.imgOnboard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return PageVH(v)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        val page = pages[position]
        // assign image (drawable). For production use Glide if large images.
        holder.img.setImageResource(page.imageRes)
    }

    override fun getItemCount(): Int = pages.size
}
