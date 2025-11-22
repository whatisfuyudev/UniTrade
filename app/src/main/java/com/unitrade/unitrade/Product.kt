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
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * File: app/src/main/java/com/unitrade/unitrade/data/model/Product.kt
 * Model data untuk dokumen "products" di Firestore.
 */
data class Product(
    var productId: String = "",
    var ownerId: String = "",
    var title: String = "",
    var description: String = "",
    var category: String = "",
    var condition: String = "",
    var price: Double = 0.0,
    var imageUrls: List<String> = emptyList(),
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp
    var createdAt: Timestamp? = null, // var agar Firestore bisa set
    @ServerTimestamp
    var updatedAt: Timestamp? = null,
    var action: String = "Jual Beli"
)
