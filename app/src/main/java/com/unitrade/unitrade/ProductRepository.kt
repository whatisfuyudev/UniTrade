package com.unitrade.unitrade

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
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
import kotlinx.coroutines.withContext
import java.util.Locale


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
     * Cari produk berdasarkan teks (title atau description).
     * - Melakukan dua query prefix: title dan description (startAt / endAt).
     * - Gabungkan hasil, hilangkan duplikat berdasarkan doc id, lalu filter case-insensitive
     *   untuk memastikan substring match (title atau description contains).
     *
     * Note: ini melakukan 2 reads per query hasil; batasi `limit` untuk mengontrol read charge.
     */
    suspend fun searchProductsByText(queryText: String, limit: Int = 50): List<Product> = withContext(Dispatchers.IO) {
        val q = queryText.trim()
        if (q.isEmpty()) return@withContext emptyList<Product>()

        // Firestore prefix trick: startAt / endAt with trailing \uf8ff
        val end = "$q\uf8ff"

        // Query title prefix
        val qTitle = productsColl
            .orderBy("title")
            .startAt(q)
            .endAt(end)
            .limit(limit.toLong())

        // Query description prefix
        val qDesc = productsColl
            .orderBy("description")
            .startAt(q)
            .endAt(end)
            .limit(limit.toLong())

        val snaps = listOf(qTitle.get().await(), qDesc.get().await())

        val merged = LinkedHashMap<String, Product>() // maintain insertion order, avoid duplicates
        for (snap in snaps) {
            for (doc in snap.documents) {
                val p = doc.toObject(Product::class.java)?.apply { productId = doc.id }
                if (p != null) merged[doc.id] = p
            }
        }

        // final filter: contain in title or description, case-insensitive
        val lower = q.lowercase(Locale.getDefault())
        val filtered = merged.values.filter { prod ->
            val t = prod.title ?: ""
            val d = prod.description ?: ""
            t.lowercase(Locale.getDefault()).contains(lower) || d.lowercase(Locale.getDefault()).contains(lower)
        }

        // limit again in case both queries produced many results
        if (filtered.size <= limit) filtered else filtered.subList(0, limit)
    }

    // Tambahkan di dalam file ProductRepository.kt (di sebelah fungsi read lainnya)
    data class PageResult<T>(
        val items: List<T>,
        val lastSnapshot: DocumentSnapshot?
    )

    /**
     * Ambil satu page produk dari koleksi "products".
     * - pageSize: jumlah item per halaman (mis. 10)
     * - lastSnapshot: DocumentSnapshot terakhir dari page sebelumnya (null untuk page pertama)
     * - orderDesc: true => urut berdasarkan createdAt DESC (Terbaru dulu), false => ASC (Terlama dulu)
     *
     * Catatan:
     * - Pastikan setiap dokumen product menyimpan field "createdAt" (FieldValue.serverTimestamp()) saat create.
     * - Fungsi ini tidak mengubah fungsi realtime yang ada (productsFlow).
     */
    suspend fun getProductsPage(
        pageSize: Int,
        lastSnapshot: DocumentSnapshot? = null,
        orderDesc: Boolean = true
    ): PageResult<Product> = withContext(Dispatchers.IO) {
        // Query: orderBy createdAt (pastikan field ini ada)
        var query = productsColl
            .orderBy("createdAt", if (orderDesc) Query.Direction.DESCENDING else Query.Direction.ASCENDING)
            .limit(pageSize.toLong())

        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        val snap = query.get().await()
        val items = snap.documents.mapNotNull { doc ->
            val p = doc.toObject(Product::class.java)
            p?.apply { productId = doc.id } // pastikan Product punya property productId: String
        }

        val last = if (snap.documents.isNotEmpty()) snap.documents.last() else null
        PageResult(items, last)
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

    /**
     * Hapus satu image dari product:
     * - menghapus asset di Cloudinary via signed delete (jika publicId tersedia atau dapat diekstrak)
     * - menghapus entry imageUrl dan imagePublicIds di dokumen Firestore (arrayRemove)
     *
     * @param productId id dokumen product
     * @param publicId public_id Cloudinary (boleh null)
     * @param imageUrl secure_url Cloudinary (boleh null)
     */
    suspend fun removeImageFromProduct(productId: String, publicId: String?, imageUrl: String?): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw Exception("Product not found")

        // determine publicId to delete
        var pidToDelete: String? = publicId
        if (pidToDelete.isNullOrBlank()) {
            pidToDelete = extractPublicIdFromCloudinaryUrl(imageUrl)
        }

        // 1) delete asset in Cloudinary (if we have publicId)
        if (!pidToDelete.isNullOrBlank()) {
            try {
                val ok = cloudinaryUploader.deleteImageSigned(pidToDelete, cloudinaryApiKey, cloudinaryApiSecret)
                // if not ok, we still attempt Firestore update; optionally you can throw
                if (!ok) {
                    // optional: throw Exception("Gagal menghapus asset di Cloudinary")
                    // we'll continue to try remove document refs even if delete failed
                }
            } catch (e: Exception) {
                // swallow but log (or rethrow if you prefer)
                e.printStackTrace()
            }
        }

        // 2) remove entries from Firestore arrays
        val updates = mutableMapOf<String, Any>()
        imageUrl?.let { updates["imageUrls"] = FieldValue.arrayRemove(it) }
        if (!publicId.isNullOrBlank()) {
            // remove the exact publicId value (if it exists)
            updates["imagePublicIds"] = FieldValue.arrayRemove(publicId)
        } else if (!pidToDelete.isNullOrBlank()) {
            // if original publicId null but we extracted a pidToDelete, remove that
            updates["imagePublicIds"] = FieldValue.arrayRemove(pidToDelete)
        }
        if (updates.isNotEmpty()) {
            // perform atomic update for each field separately because FieldValue used
            // Firestore supports multiple fields, so pass updates map
            docRef.update(updates as Map<String, Any>).await()
        }
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
