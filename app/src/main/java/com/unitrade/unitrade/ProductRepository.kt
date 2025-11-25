package com.unitrade.unitrade

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Named
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File: app/src/main/java/com/unitrade/unitrade/data/repo/ProductRepository.kt
 *
 * Repository untuk operasi CRUD produk:
 * - addProduct: upload gambar ke Cloudinary (mengembalikan UploadResult) -> ambil secureUrl -> simpan di Firestore
 * - updateProduct: upload gambar baru bila ada (gunakan UploadResult.secureUrl), merge imageUrls
 * - deleteProduct: menghapus dokumen product (tidak menghapus file Cloudinary di client)
 *
 * Catatan:
 * - CloudinaryUploader.uploadImage(file, folder) sekarang mengembalikan UploadResult? (secureUrl + publicId).
 *   Kode ini mengambil secureUrl dari UploadResult dan menyimpan ke imageUrls.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @Named("cloudinary_products") private val cloudinaryUploader: CloudinaryUploader,
    @Named("cloudinary_api_key") private val cloudinaryApiKey: String,
    @Named("cloudinary_api_secret") private val cloudinaryApiSecret: String
) {
    private val productsColl get() = firestore.collection("products")

    suspend fun addProduct(product: Product, imageFiles: List<File>, folder: String): String = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val docRef = productsColl.document()
        val productId = docRef.id

        // upload images (sequential; bisa paralelisasi jika ingin)
        val uploadedUrls = mutableListOf<String>()
        val uploadedPublicIds = mutableListOf<String?>()

        for (file in imageFiles) {
            val result = cloudinaryUploader.uploadImage(file, folder)
                ?: throw Exception("Upload failed for ${file.name}")
            // result.secureUrl wajib ada, publicId bisa null
            uploadedUrls.add(result.secureUrl)
            uploadedPublicIds.add(result.publicId)
        }

        val data = hashMapOf<String, Any?>(
            "productId" to productId,
            "ownerId" to user.uid,
            "title" to product.title,
            "description" to product.description,
            "category" to product.category,
            "condition" to product.condition,
            "price" to product.price,
            "imageUrls" to uploadedUrls,
            // optional: simpan publicIds jika ingin nanti hapus file dari cloudinary
            "imagePublicIds" to uploadedPublicIds,
            "isActive" to product.isActive,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "action" to product.action
        )

        docRef.set(data).await()
        productId
    }

    /**
     * Update product:
     * - jika ada newImageFiles: upload dan tambahkan secureUrl ke daftar existingUrls
     * - mergedUrls disimpan kembali di dokumen
     */
    suspend fun updateProduct(productId: String, updatedFields: Product, newImageFiles: List<File>?, folder: String): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw Exception("Product not found")

        val existingUrls = snapshot.get("imageUrls") as? List<String> ?: emptyList()
        val existingPublicIds = snapshot.get("imagePublicIds") as? List<String?> ?: emptyList()

        val newUploadedUrls = mutableListOf<String>()
        val newUploadedPublicIds = mutableListOf<String?>()

        if (!newImageFiles.isNullOrEmpty()) {
            for (file in newImageFiles) {
                val result = cloudinaryUploader.uploadImage(file, folder)
                    ?: throw Exception("Upload failed for ${file.name}")
                newUploadedUrls.add(result.secureUrl)
                newUploadedPublicIds.add(result.publicId)
            }
        }

        val mergedUrls = existingUrls.toMutableList()
        mergedUrls.addAll(newUploadedUrls)

        val mergedPublicIds = existingPublicIds.toMutableList()
        mergedPublicIds.addAll(newUploadedPublicIds)

        val updateMap = hashMapOf<String, Any?>(
            "title" to updatedFields.title,
            "description" to updatedFields.description,
            "category" to updatedFields.category,
            "condition" to updatedFields.condition,
            "price" to updatedFields.price,
            "action" to updatedFields.action,
            "isActive" to updatedFields.isActive,
            "imageUrls" to mergedUrls,
            "imagePublicIds" to mergedPublicIds,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        docRef.update(updateMap as Map<String, Any?>).await()
    }

    suspend fun deleteProduct(productId: String): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        docRef.delete().await()
        // Note: file deletion in Cloudinary should be done via backend or signed API (not handled here).
    }

    fun productsFlow(): Flow<List<Product>> = callbackFlow {
        val query = firestore.collection("products")
            .whereEqualTo("isActive", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Product::class.java)
            } ?: emptyList()
            trySend(list).isSuccess
        }

        awaitClose {
            registration.remove()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getProductOnce(productId: String): Product? {
        val docRef = firestore.collection("products").document(productId)
        val snapshot = docRef.get().await()
        return snapshot.toObject(Product::class.java)
    }

    /**
     * Ambil daftar produk berdasarkan daftar productId.
     * - Menggunakan batching untuk mengatasi batas whereIn (10 items).
     * - Mengembalikan list produk dalam urutan tidak dijamin (urut berdasarkan query hasil Firestore).
     */
    suspend fun getProductsByIds(ids: List<String>): List<Product> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList<Product>()

        val results = mutableListOf<Product>()
        // Firestore whereIn limit = 10 items per query -> chunk
        val chunks = ids.chunked(10)
        for (chunk in chunks) {
            val query = productsColl.whereIn("productId", chunk)
            val snap = query.get().await()
            val docs = snap.documents
            for (doc in docs) {
                val product = doc.toObject(Product::class.java)
                if (product != null) results.add(product)
            }
        }
        results
    }

    /**
     * Mengambil semua produk yang dimiliki oleh ownerId (suspend).
     */
    suspend fun getProductsByOwner(ownerId: String): List<Product> = withContext(Dispatchers.IO) {
        val query = productsColl.whereEqualTo("ownerId", ownerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
        val snap = query.get().await()
        snap.documents.mapNotNull { doc -> doc.toObject(Product::class.java) }
    }

    /**
     * Menghapus produk dan mencoba menghapus file gambar di Cloudinary (client-side signed delete).
     *
     * @param productId id dokumen product
     * @param imageUrls optional list of secure_url (dipakai untuk heuristik bila imagePublicIds tidak tersedia)
     */
    suspend fun deleteProduct(productId: String, imageUrls: List<String>? = null): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            // nothing to delete, but return normally
            return@withContext
        }

        // 1) try to read stored public ids
        val storedPublicIds = snapshot.get("imagePublicIds") as? List<String?>
        val toDeletePublicIds = mutableListOf<String>()

        if (!storedPublicIds.isNullOrEmpty()) {
            // use stored public ids if present
            for (pid in storedPublicIds) {
                if (!pid.isNullOrBlank()) toDeletePublicIds.add(pid)
            }
        } else {
            // fallback: try to derive public id from imageUrls (heuristic)
            val urls = imageUrls ?: (snapshot.get("imageUrls") as? List<String>) ?: emptyList()
            for (url in urls) {
                val pid = extractPublicIdFromCloudinaryUrl(url)
                if (!pid.isNullOrBlank()) toDeletePublicIds.add(pid)
            }
        }

        // 2) attempt delete each publicId using signed API (client-side)
        for (publicId in toDeletePublicIds) {
            try {
                // deleteImageSigned returns Boolean
                val ok = cloudinaryUploader.deleteImageSigned(publicId, cloudinaryApiKey, cloudinaryApiSecret)
                // optional: log result (no logger here)
            } catch (e: Exception) {
                // swallow errors to ensure we still delete doc; you may want to surface error to UI
                e.printStackTrace()
            }
        }

        // 3) delete firestore doc
        docRef.delete().await()
    }

    // helper: ekstraksi public_id dari secure_url Cloudinary
    private fun extractPublicIdFromCloudinaryUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        try {
            // contoh URL:
            // https://res.cloudinary.com/<cloud>/image/upload/v1670000000/unitrade-products-pictures/abc_def-01.jpg
            // langkah: ambil substring setelah "/image/upload/"
            val marker = "/image/upload/"
            if (!url.contains(marker)) return null
            var tail = url.substringAfter(marker) // mungkin dimulai dengan "v12345/..."
            // hilangkan versi jika ada (v\d+/)
            tail = tail.replaceFirst(Regex("^v\\d+/"), "")
            // hapus query string jika ada
            if (tail.contains("?")) tail = tail.substringBefore("?")
            // hapus extension
            val noExt = tail.substringBeforeLast('.')
            // noExt sekarang seharusnya "unitrade-products-pictures/abc_def-01" atau "abc_def-01"
            return noExt
        } catch (e: Exception) {
            return null
        }
    }
}
