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
import java.util.Locale

/**
 * File: app/src/main/java/com/unitrade/unitrade/ProductRepository.kt
 *
 * Repository untuk operasi CRUD produk:
 * - addProduct: upload gambar ke Cloudinary -> simpan secureUrl ke imageUrls di Firestore
 * - updateProduct: upload gambar baru bila ada, merge imageUrls
 * - deleteProduct: menghapus dokumen product; optional: coba hapus asset di Cloudinary via signed API
 * - getProductOnce(productId): fetch single product document (helper yang dipakai oleh UI)
 * - searchProductsByText, pagination helpers, batch get by ids, owner lookup, remove image, dll.
 *
 * Catatan:
 * - CloudinaryUploader.uploadImage(file, folder) diharapkan mengembalikan UploadResult? (secureUrl + publicId)
 * - Menghapus asset Cloudinary yang aman sebaiknya dilakukan di backend; fungsi deleteImageSigned disediakan
 *   hanya untuk demo/content yang sudah kamu setujui.
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

    /**
     * Tambah produk baru:
     * - upload imageFiles ke Cloudinary folder yang diberikan
     * - simpan dokumen product di Firestore
     * - mengembalikan productId (doc id)
     */
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
            "imagePublicIds" to uploadedPublicIds,
            "isActive" to product.isActive,
            "isSold" to false,
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
     * - simpan merged lists ke dokumen
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

    /**
     * Hapus produk (doc) dan coba hapus asset Cloudinary bila publicId tersedia.
     * Perlu catatan keamanan: penggunaan apiSecret di client tidak aman untuk produksi.
     */
    suspend fun deleteProduct(productId: String): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        docRef.delete().await()
        // Note: file deletion in Cloudinary ideally done via backend; this client method does not delete assets
    }

    /**
     * Realtime flow produk aktif
     */
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

        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Ambil satu product dokumen sekali (helper untuk UI)
     */
    suspend fun getProductOnce(productId: String): Product? {
        val docRef = firestore.collection("products").document(productId)
        val snapshot = docRef.get().await()
        return snapshot.toObject(Product::class.java)
    }

    /**
     * Pencarian sederhana berdasarkan prefix pada title atau description.
     * Menggunakan teknik startAt / endAt dengan '\uf8ff'.
     */
    suspend fun searchProductsByText(queryText: String, limit: Int = 50): List<Product> = withContext(Dispatchers.IO) {
        val q = queryText.trim()
        if (q.isEmpty()) return@withContext emptyList<Product>()

        val end = "$q\uf8ff"

        val qTitle = productsColl
            .orderBy("title")
            .startAt(q)
            .endAt(end)
            .limit(limit.toLong())

        val qDesc = productsColl
            .orderBy("description")
            .startAt(q)
            .endAt(end)
            .limit(limit.toLong())

        val snaps = listOf(qTitle.get().await(), qDesc.get().await())

        val merged = LinkedHashMap<String, Product>()
        for (snap in snaps) {
            for (doc in snap.documents) {
                val p = doc.toObject(Product::class.java)?.apply { productId = doc.id }
                if (p != null) merged[doc.id] = p
            }
        }

        val lower = q.lowercase(Locale.getDefault())
        val filtered = merged.values.filter { prod ->
            val t = prod.title ?: ""
            val d = prod.description ?: ""
            t.lowercase(Locale.getDefault()).contains(lower) || d.lowercase(Locale.getDefault()).contains(lower)
        }

        if (filtered.size <= limit) filtered else filtered.subList(0, limit)
    }

    data class PageResult<T>(
        val items: List<T>,
        val lastSnapshot: DocumentSnapshot?
    )

    /**
     * Pagination helper - ambil satu page produk.
     */
    suspend fun getProductsPage(
        pageSize: Int,
        lastSnapshot: DocumentSnapshot? = null,
        orderDesc: Boolean = true
    ): PageResult<Product> = withContext(Dispatchers.IO) {
        var query = productsColl
            .orderBy("createdAt", if (orderDesc) Query.Direction.DESCENDING else Query.Direction.ASCENDING)
            .limit(pageSize.toLong())

        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        val snap = query.get().await()
        val items = snap.documents.mapNotNull { doc ->
            val p = doc.toObject(Product::class.java)
            p?.apply { productId = doc.id }
        }

        val last = if (snap.documents.isNotEmpty()) snap.documents.last() else null
        PageResult(items, last)
    }

    /**
     * Ambil produk berdasarkan daftar ids (batched karena whereIn limit = 10).
     */
    suspend fun getProductsByIds(ids: List<String>): List<Product> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList<Product>()

        val results = mutableListOf<Product>()
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
     * Ambil semua produk milik ownerId.
     */
    suspend fun getProductsByOwner(ownerId: String): List<Product> = withContext(Dispatchers.IO) {
        val query = productsColl.whereEqualTo("ownerId", ownerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
        val snap = query.get().await()
        snap.documents.mapNotNull { doc -> doc.toObject(Product::class.java) }
    }

    /**
     * Hapus produk lengkap - coba hapus asset Cloudinary jika public ids tersedia,
     * lalu hapus dokumen Firestore.
     */
    suspend fun deleteProduct(productId: String, imageUrls: List<String>? = null): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) return@withContext

        val storedPublicIds = snapshot.get("imagePublicIds") as? List<String?>
        val toDeletePublicIds = mutableListOf<String>()

        if (!storedPublicIds.isNullOrEmpty()) {
            for (pid in storedPublicIds) if (!pid.isNullOrBlank()) toDeletePublicIds.add(pid)
        } else {
            val urls = imageUrls ?: (snapshot.get("imageUrls") as? List<String>) ?: emptyList()
            for (url in urls) {
                val pid = extractPublicIdFromCloudinaryUrl(url)
                if (!pid.isNullOrBlank()) toDeletePublicIds.add(pid)
            }
        }

        for (publicId in toDeletePublicIds) {
            try {
                cloudinaryUploader.deleteImageSigned(publicId, cloudinaryApiKey, cloudinaryApiSecret)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        docRef.delete().await()
    }

    /**
     * Hapus satu image dari product: delete asset via signed API (if possible) and remove references in Firestore.
     */
    suspend fun removeImageFromProduct(productId: String, publicId: String?, imageUrl: String?): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw Exception("Product not found")

        var pidToDelete: String? = publicId
        if (pidToDelete.isNullOrBlank()) pidToDelete = extractPublicIdFromCloudinaryUrl(imageUrl)

        if (!pidToDelete.isNullOrBlank()) {
            try {
                cloudinaryUploader.deleteImageSigned(pidToDelete, cloudinaryApiKey, cloudinaryApiSecret)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val updates = mutableMapOf<String, Any>()
        imageUrl?.let { updates["imageUrls"] = FieldValue.arrayRemove(it) }
        if (!publicId.isNullOrBlank()) updates["imagePublicIds"] = FieldValue.arrayRemove(publicId)
        else if (!pidToDelete.isNullOrBlank()) updates["imagePublicIds"] = FieldValue.arrayRemove(pidToDelete)

        if (updates.isNotEmpty()) {
            docRef.update(updates as Map<String, Any>).await()
        }
    }

    /**
     * Mark product as sold dan kirim notifikasi ke semua user yang memfavoritkan produk ini
     */
    suspend fun markProductAsSold(productId: String): Unit = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        
        // Update status produk
        val productRef = productsColl.document(productId)
        productRef.update(
            mapOf(
                "isSold" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        
        // Ambil data produk untuk notifikasi
        val productSnapshot = productRef.get().await()
        val product = productSnapshot.toObject(Product::class.java)
        val productTitle = product?.title ?: "Produk"
        
        // Cari semua user yang memfavoritkan produk ini
        val usersSnapshot = firestore.collection("users")
            .whereArrayContains("favorites", productId)
            .get()
            .await()
        
        // Kirim notifikasi ke setiap user
        for (userDoc in usersSnapshot.documents) {
            val userId = userDoc.id
            if (userId == user.uid) continue // Skip pemilik produk
            
            val fcmToken = userDoc.getString("fcmToken")
            if (!fcmToken.isNullOrBlank()) {
                sendSoldNotification(fcmToken, productTitle, productId)
            }
        }
    }

    /**
     * Kirim notifikasi menggunakan FCM
     * Karena free tier, kita akan simpan notifikasi di Firestore untuk dibaca client
     */
    private suspend fun sendSoldNotification(fcmToken: String, productTitle: String, productId: String) {
        // Simpan notifikasi di collection "notifications" 
        // Client akan listen dan menampilkan notifikasi lokal
        val notificationData = hashMapOf(
            "token" to fcmToken,
            "title" to "Produk Favorit Terjual!",
            "body" to "\"$productTitle\" yang Anda favoritkan sudah terjual",
            "productId" to productId,
            "timestamp" to FieldValue.serverTimestamp(),
            "read" to false
        )
        
        firestore.collection("pending_notifications")
            .add(notificationData)
            .await()
    }

    // helper: extract public_id from typical Cloudinary secure url
    private fun extractPublicIdFromCloudinaryUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        try {
            val marker = "/image/upload/"
            if (!url.contains(marker)) return null
            var tail = url.substringAfter(marker)
            tail = tail.replaceFirst(Regex("^v\\d+/"), "")
            if (tail.contains("?")) tail = tail.substringBefore("?")
            val noExt = tail.substringBeforeLast('.')
            return noExt
        } catch (e: Exception) {
            return null
        }
    }
}
