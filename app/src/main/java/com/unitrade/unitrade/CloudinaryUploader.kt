package com.unitrade.unitrade

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * app/src/main/java/com/unitrade/unitrade/data/network/CloudinaryUploader.kt
 *
 * Utility untuk upload file gambar ke Cloudinary via unsigned upload preset.
 * - Gunakan unsigned preset ketika kamu tidak punya backend untuk sign. Unsigned kurang aman.
 * - Untuk signed uploads gunakan backend untuk sign parameter.
 *
 * Usage:
 *   val uploader = CloudinaryUploader(cloudName, uploadPreset)
 *   val url = uploader.uploadImage(file)
 *
 * Note: network call dilakukan di IO dispatcher.
 */
class CloudinaryUploader(
    private val cloudName: String,
    private val uploadPreset: String
) {
    private val client = OkHttpClient()

    // UNSIGNED upload: tambahkan folder sebagai field form
    suspend fun uploadImage(file: File, folder: String? = null): String? = withContext(Dispatchers.IO) {
        val url = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"
        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .addFormDataPart("upload_preset", uploadPreset)

        // <-- pastikan folder dikirim di sini
        folder?.let { builder.addFormDataPart("folder", it) }

        val requestBody = builder.build()
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val regex = "\"secure_url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = regex.find(body)
            match?.groups?.get(1)?.value
        }
    }

    // SIGNED upload (tidak direkomendasikan di client; lebih aman dilakukan lewat backend)
    suspend fun uploadImageSigned(file: File, apiKey: String, apiSecret: String, folder: String? = null): String? = withContext(Dispatchers.IO) {
        val url = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        // Build param string to sign (sorted)
        val params = mutableListOf("timestamp=$timestamp")
        folder?.let { params.add("folder=$it") }
        val toSign = params.sorted().joinToString("&")
        val signature = sha1("$toSign$apiSecret")

        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .addFormDataPart("api_key", apiKey)
            .addFormDataPart("timestamp", timestamp)
            .addFormDataPart("signature", signature)

        folder?.let { builder.addFormDataPart("folder", it) }

        val requestBody = builder.build()
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val regex = "\"secure_url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = regex.find(body)
            match?.groups?.get(1)?.value
        }
    }

    private fun sha1(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}