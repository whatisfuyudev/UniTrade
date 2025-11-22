package com.unitrade.unitrade

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unitrade.unitrade.CloudinaryUploader
import com.unitrade.unitrade.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File: app/src/main/java/com/unitrade/unitrade/data/repo/ProductRepository.kt
 *
 * Tugas:
 * - addProduct: upload gambar ke Cloudinary, simpan doc baru di "products"
 * - updateProduct: upload gambar baru bila ada, update doc fields (merge)
 * - deleteProduct: delete doc di Firestore (tidak menghapus file di Cloudinary)
 *
 * Note: cloudinaryUploader.uploadImage(file, folder) harus mengembalikan secure_url string.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cloudinaryUploader: CloudinaryUploader
) {
    private val productsColl get() = firestore.collection("products")

    suspend fun addProduct(product: Product, imageFiles: List<File>, folder: String): String = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val docRef = productsColl.document()
        val productId = docRef.id

        // upload images (sequential; bisa di parallel jika perlu)
        val uploadedUrls = mutableListOf<String>()
        for (file in imageFiles) {
            val url = cloudinaryUploader.uploadImage(file, folder)
                ?: throw Exception("Upload failed for ${file.name}")
            uploadedUrls.add(url)
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
     * - productId: id dokumen yang akan diupdate
     * - updatedFields: Product object with new simple fields (title, desc, category, price, action, condition, isActive)
     * - newImageFiles: optional list of new File to upload; resulting imageUrls will be (existingUrls + uploadedNewUrls)
     * - folder: cloudinary folder
     */
    suspend fun updateProduct(productId: String, updatedFields: Product, newImageFiles: List<File>?, folder: String): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw Exception("Product not found")

        val existingUrls = snapshot.get("imageUrls") as? List<String> ?: emptyList()
        val newUploaded = mutableListOf<String>()

        if (!newImageFiles.isNullOrEmpty()) {
            for (file in newImageFiles) {
                val url = cloudinaryUploader.uploadImage(file, folder)
                    ?: throw Exception("Upload failed for ${file.name}")
                newUploaded.add(url)
            }
        }

        val mergedUrls = existingUrls.toMutableList()
        mergedUrls.addAll(newUploaded)

        val updateMap = hashMapOf<String, Any?>(
            "title" to updatedFields.title,
            "description" to updatedFields.description,
            "category" to updatedFields.category,
            "condition" to updatedFields.condition,
            "price" to updatedFields.price,
            "action" to updatedFields.action,
            "isActive" to updatedFields.isActive,
            "imageUrls" to mergedUrls,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // perform update
        docRef.update(updateMap as Map<String, Any?>).await()
    }

    suspend fun deleteProduct(productId: String): Unit = withContext(Dispatchers.IO) {
        val docRef = productsColl.document(productId)
        docRef.delete().await()
        // Note: file deletion on Cloudinary not handled client-side in this implementation.
    }

    // existing methods like productsFlow() and getProductOnce() remain usable
    /**
     * productsFlow:
     * - Mengembalikan Flow yang meng-emits daftar produk aktif (isActive == true)
     * - Order by createdAt desc (baru tampil lebih dulu)
     * - Menggunakan addSnapshotListener internally; listener akan dihapus ketika collector dibatalkan.
     *
     * Alasan menggunakan Flow:
     * - Mudah dipakai di ViewModel/Fragment dengan repeatOnLifecycle untuk lifecycle-safety.
     */
    fun productsFlow(): Flow<List<Product>> = callbackFlow {
        val query = firestore.collection("products")
            .whereEqualTo("isActive", true)
            // Jika createdAt disimpan sebagai Timestamp gunakan field tersebut
            .orderBy("createdAt", Query.Direction.DESCENDING)

        // Pasang snapshot listener
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Kirim error lewat channel sebagai empty list atau log; di sini kita close dengan exception
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                // Gunakan toObject agar mapping otomatis
                doc.toObject(Product::class.java)
            } ?: emptyList()
            trySend(list).isSuccess // ignore backpressure
        }

        // Saat collector cancelled/closed, lepaskan listener
        awaitClose {
            registration.remove()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Ambil satu product secara sekali (suspend).
     * Menggunakan Tasks.await via kotlinx-coroutines-play-services.
     */
    suspend fun getProductOnce(productId: String): Product? {
        val docRef = firestore.collection("products").document(productId)
        val snapshot = docRef.get().await()
        return snapshot.toObject(Product::class.java)
    }

}
