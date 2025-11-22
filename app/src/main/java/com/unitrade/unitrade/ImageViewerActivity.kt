package com.unitrade.unitrade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.unitrade.unitrade.R
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

/**
 * app/src/main/java/com/unitrade/unitrade/ui/chat/ImageViewerActivity.kt
 *
 * Activity full-screen untuk menampilkan satu gambar dengan pinch-to-zoom & pan.
 * - Terima EXTRA_IMAGE_URL (String) via Intent.
 * - Jika string adalah path lokal (dimulai dengan "/mnt/" atau "file://"), load sebagai File.
 * - Jika string adalah http/https, load via URL.
 *
 * Usage:
 *   val intent = ImageViewerActivity.createIntent(context, imageUrl)
 *   context.startActivity(intent)
 *
 * Jangan simpan kredensial atau secret di sini.
 */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"

        fun createIntent(context: Context, imageUrl: String): Intent {
            return Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URL, imageUrl)
            }
        }
    }

    private lateinit var photoView: PhotoView
    private lateinit var btnClose: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // fullscreen appearance; rely on theme for status bar if needed
        setContentView(R.layout.activity_image_viewer)

        photoView = findViewById(R.id.photoView)
        btnClose = findViewById(R.id.btnClose)

        btnClose.setOnClickListener {
            finish()
        }

        val imageUrl = intent?.getStringExtra(EXTRA_IMAGE_URL)
        if (imageUrl.isNullOrBlank()) {
            finish()
            return
        }

        // Load image with Glide; support local file path or remote url
        when {
            imageUrl.startsWith("/mnt/") || imageUrl.startsWith("/storage/") ->
                // local file path
                Glide.with(this)
                    .load(File(imageUrl))
                    .placeholder(R.drawable.placeholder)
                    .into(photoView)

            imageUrl.startsWith("file://") ->
                Glide.with(this)
                    .load(Uri.parse(imageUrl))
                    .placeholder(R.drawable.placeholder)
                    .into(photoView)

            else ->
                // assume http/https
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder)
                    .into(photoView)
        }

        // PhotoView already handles pinch-to-zoom and pan gestures
    }
}
