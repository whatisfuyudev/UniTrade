package com.unitrade.unitrade

/*
File: app/src/main/java/com/unitrade/unitrade/data/repository/ProductRepository.kt

Deskripsi:
Repository bertanggung jawab berinteraksi dengan Firestore untuk entitas Product.
- Menyediakan Flow<List<Product>> realtime (productsFlow) menggunakan callbackFlow,
  sehingga UI dapat meng-collect data secara lifecycle-aware.
- Menyediakan suspend function getProductOnce untuk mengambil satu dokumen (once) dengan coroutines.

Teknologi & praktik:
- Menggunakan FirebaseFirestore KTX + kotlinx-coroutines-play-services (tasks.await).
- callbackFlow untuk streaming snapshot listener (rekomendasi modern).
- flowOn(Dispatchers.IO) agar operasi network/IO tidak di main thread.
- Ditandai @Singleton untuk injeksi Hilt.
*/

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unitrade.unitrade.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

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
