package com.unitrade.unitrade

/*
 File: app/src/main/java/com/unitrade/unitrade/data/model/Product.kt

 Model data untuk dokumen "products" di Firestore.
 Perubahan 2025-11:
  - Menambahkan property `imagePublicIds: List<String?>?` supaya kita bisa menyimpan public_id Cloudinary
    saat upload. Ini memudahkan proses penghapusan gambar di Cloudinary (delete signed).
  - Menjaga default values agar `toObject(Product::class.java)` aman terhadap dokumen lama.
*/

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class Product(
    var productId: String = "",
    var ownerId: String = "",
    var title: String = "",
    var description: String = "",
    var category: String = "",
    var condition: String = "",
    var price: Double = 0.0,

    /**
     * List of secure URLs for images stored in Cloudinary (visible to client).
     * Example: https://res.cloudinary.com/.../unitrade-products-pictures/abc_def-01.jpg
     */
    var imageUrls: List<String> = emptyList(),

    /**
     * Optional: list of Cloudinary public_id values for each image.
     * - public_id is useful to perform deletion via Cloudinary API.
     * - We keep it nullable per item because some uploads might not return public_id.
     */
    @get:PropertyName("imagePublicIds") @set:PropertyName("imagePublicIds")
    var imagePublicIds: List<String?>? = emptyList(),

    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,

    @ServerTimestamp
    var createdAt: Timestamp? = null,  // var so Firestore can set serverTimestamp

    @ServerTimestamp
    var updatedAt: Timestamp? = null,

    var action: String = "Jual Beli"
)
