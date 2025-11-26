package com.unitrade.unitrade

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.unitrade.unitrade.R
import com.unitrade.unitrade.Product
import com.unitrade.unitrade.ProductRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * app/src/main/java/com/unitrade/unitrade/ProductDetailFragment.kt
 *
 * Deskripsi:
 * - Menampilkan detail produk
 * - Menyediakan tombol chat yang akan:
 *     * jika user belum login -> navigasi ke LoginFragment
 *     * jika user login -> membuat/ambil chat thread deterministik per-product melalui
 *       ChatRepository.getOrCreateThreadWith(myUid, ownerId, productId)
 *       lalu menavigasi ke ChatDetailFragment dengan arg "chatId"
 *
 * Perubahan utama:
 * - Pada klik btnChat, sekarang memanggil getOrCreateThreadWith(..., productId)
 *   sehingga thread bersifat per-product (chat_prod_{productId}_{u1}_{u2}).
 *
 * Lokasi file: app/src/main/java/com/unitrade/unitrade/ProductDetailFragment.kt
 */
@AndroidEntryPoint
class ProductDetailFragment : Fragment(R.layout.fragment_product_detail) {

    @Inject lateinit var repository: ProductRepository
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var auth: FirebaseAuth

    private var productId: String? = null
    private var currentProduct: Product? = null

    // UI refs
    private var vpImages: ViewPager2? = null
    private var btnBack: ImageButton? = null
    private var btnFavorite: ImageButton? = null
    private var btnChat: com.google.android.material.button.MaterialButton? = null
    private var tvTitle: TextView? = null
    private var tvPrice: TextView? = null
    private var tvCondition: TextView? = null
    private var tvDescription: TextView? = null

    // seller views
    private var imgSellerAvatar: ImageView? = null
    private var tvSellerName: TextView? = null
    private var tvSellerFaculty: TextView? = null
    private var tvSellerContact: TextView? = null
    private var tvAction: TextView? = null

    private var isFavorite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productId = arguments?.getString("productId")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // bind views
        vpImages = view.findViewById(R.id.vpImages)
        btnBack = view.findViewById(R.id.btnBack)
        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnChat = view.findViewById(R.id.btnChatSeller)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvPrice = view.findViewById(R.id.tvPrice)
        tvCondition = view.findViewById(R.id.tvCondition)
        tvDescription = view.findViewById(R.id.tvDescription)

        imgSellerAvatar = view.findViewById(R.id.imgSellerAvatar)
        tvSellerName = view.findViewById(R.id.tvSellerName)
        tvSellerFaculty = view.findViewById(R.id.tvSellerFaculty)
        tvSellerContact = view.findViewById(R.id.tvSellerContact)
        tvAction = view.findViewById(R.id.tvAction)

        btnBack?.setOnClickListener { findNavController().popBackStack() }

        // favorite toggle
        btnFavorite?.setOnClickListener {
            toggleFavorite()
        }

        // chat button behaviour: create/get per-product thread and navigate to chatDetail
        btnChat?.setOnClickListener {
            btnChat?.isEnabled = false
            val currentUid = chatRepository.auth.currentUser?.uid
            if (currentUid == null) {
                Toast.makeText(requireContext(), "Harap login terlebih dahulu untuk menghubungi penjual", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.loginFragment)
                btnChat?.isEnabled = true
                return@setOnClickListener
            }

            val ownerId = currentProduct?.ownerId
            if (ownerId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Informasi penjual tidak tersedia", Toast.LENGTH_SHORT).show()
                btnChat?.isEnabled = true
                return@setOnClickListener
            }

            if (ownerId == currentUid) {
                Toast.makeText(requireContext(), "Ini adalah produk milik Anda", Toast.LENGTH_SHORT).show()
                btnChat?.isEnabled = true
                return@setOnClickListener
            }

            // create or get product-scoped thread then navigate
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // IMPORTANT: pass productId so thread is deterministic per-product
                    val pid = productId // may be null; getOrCreateThreadWith handles null
                    val chatId = chatRepository.getOrCreateThreadWith(currentUid, ownerId, pid)
                    val bundle = Bundle().apply { putString("chatId", chatId) }
                    findNavController().navigate(R.id.chatDetailFragment, bundle)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memulai chat: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    btnChat?.isEnabled = true
                }
            }
        }

        // load product + seller + favorite state lifecycle-safe
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                productId?.let { id ->
                    try {
                        val product = repository.getProductOnce(id)
                        if (product != null) {
                            currentProduct = product
                            bindProduct(product)
                            loadSellerInfo(product.ownerId)
                            // load favorite status for current user
                            loadFavoriteStatus(id)
                        } else {
                            Toast.makeText(requireContext(), "Produk tidak ditemukan", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Gagal memuat produk: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } ?: run {
                    Toast.makeText(requireContext(), "productId tidak tersedia", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindProduct(product: Product) {
        tvTitle?.text = product.title
        tvPrice?.text = formatPrice(product.price)
        tvCondition?.text = product.condition
        tvDescription?.text = product.description
        tvAction?.text = product.action ?: "-"

        // prepare image list: filter out "-" or blank placeholders
        val imgs = product.imageUrls.filter { it.isNotBlank() && it != "-" }
        val finalList = if (imgs.isEmpty()) listOf<String>() else imgs

        // set adapter
        val adapter = ImagePagerAdapter(finalList) { url ->
            // open ImageViewerActivity
            val intent = ImageViewerActivity.createIntent(requireContext(), url)
            startActivity(intent)
        }
        vpImages?.adapter = adapter
        vpImages?.offscreenPageLimit = 1
    }

    /**
     * Ambil info penjual dari collection "users" berdasarkan ownerId dan bind ke UI.
     */
    private fun loadSellerInfo(ownerId: String?) {
        if (ownerId.isNullOrBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val doc = firestore.collection("users").document(ownerId).get().await()
                if (doc.exists()) {
                    val m = doc.data
                    val displayName = m?.get("displayName") as? String ?: "-"
                    val faculty = m?.get("faculty") as? String ?: "-"
                    val contact = m?.get("contact") as? String ?: "-"
                    val photoUrl = m?.get("photoUrl") as? String

                    tvSellerName?.text = displayName
                    tvSellerFaculty?.text = faculty
                    tvSellerContact?.text = contact

                    val url = photoUrl?.takeIf { it != "-" && it.isNotBlank() }
                    if (!url.isNullOrBlank()) {
                        Glide.with(this@ProductDetailFragment).load(url).placeholder(R.drawable.placeholder).into(imgSellerAvatar!!)
                    } else {
                        imgSellerAvatar?.setImageResource(R.drawable.placeholder)
                    }
                }
            } catch (e: Exception) {
                // ignore or show small toast
            }
        }
    }

    /**
     * Cek apakah produk ini sudah ada di favorites user saat ini
     * dan render ikon favourite accordingly.
     */
    private fun loadFavoriteStatus(productId: String) {
        val uid = auth.currentUser?.uid ?: run {
            setFavoriteIcon(false)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val favs = doc.get("favorites") as? List<*>
                    isFavorite = favs?.contains(productId) == true
                    setFavoriteIcon(isFavorite)
                } else {
                    isFavorite = false
                    setFavoriteIcon(false)
                }
            } catch (e: Exception) {
                isFavorite = false
                setFavoriteIcon(false)
            }
        }
    }

    /**
     * Toggle favorite state: jika belum favorite -> add; jika sudah -> remove
     * Menggunakan FieldValue.arrayUnion / arrayRemove pada dokumen user.
     */
    private fun toggleFavorite() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // not logged in -> navigasi ke login
            Toast.makeText(requireContext(), "Silakan login untuk menyimpan favorit", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.loginFragment)
            return
        }

        val pid = currentProduct?.productId ?: return

        // optimistic UI change
        val willBeFavorite = !isFavorite
        setFavoriteIcon(willBeFavorite)
        // launch update
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userDocRef = firestore.collection("users").document(uid)
                if (willBeFavorite) {
                    userDocRef.update("favorites", FieldValue.arrayUnion(pid)).await()
                    isFavorite = true
                } else {
                    userDocRef.update("favorites", FieldValue.arrayRemove(pid)).await()
                    isFavorite = false
                }
            } catch (e: Exception) {
                // revert UI
                setFavoriteIcon(isFavorite)
                Toast.makeText(requireContext(), "Gagal update favorit: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setFavoriteIcon(fav: Boolean) {
        val res = if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        btnFavorite?.setImageResource(res)
    }

    private fun formatPrice(value: Double): String {
        val formatted = java.text.NumberFormat.getInstance(java.util.Locale("in", "ID")).format(value.toLong())
        return "Rp $formatted"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vpImages = null
        btnBack = null
        btnFavorite = null
        btnChat = null
        tvTitle = null
        tvPrice = null
        tvCondition = null
        tvDescription = null
        imgSellerAvatar = null
        tvSellerName = null
        tvSellerFaculty = null
        tvSellerContact = null
        tvAction = null
    }
}
