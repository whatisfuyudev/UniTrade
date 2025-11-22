package com.unitrade.unitrade

/*
File: app/src/main/java/com/unitrade/unitrade/data/model/Product.kt

Deskripsi:
Model data (data class) yang merepresentasikan dokumen produk dari koleksi "products" di Firestore.
Gunakan kelas ini untuk mapping otomatis `DocumentSnapshot.toObject(Product::class.java)`.

Catatan implementasi:
- Pastikan field di Firestore (nama & tipe) konsisten dengan properti di bawah.
- Untuk timestamp gunakan `com.google.firebase.Timestamp` jika kamu menyimpan timestamp Firestore,
  atau gunakan String/Long jika menyimpan ISO-string / epoch millis.
- Default values diperlukan agar `.toObject(Product::class.java)` tidak gagal bila field hilang.
*/

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Product(
    val productId: String = "",
    val ownerId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val condition: String = "",
    val price: Double = 0.0,
    val imageUrls: List<String> = emptyList(),
    val isActive: Boolean = true,
    // Gunakan Timestamp jika dokumen menggunakan timestamp Firestore
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    val action: String? = null
)
