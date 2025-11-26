package com.unitrade.unitrade

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.databinding.FragmentAddEditProductBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * app/src/main/java/com/unitrade/unitrade/AddEditProductFragment.kt
 *
 * Fragment untuk menambah / mengedit produk.
 *
 * - Jika isEditMode == true -> gunakan MixedImagePreviewAdapter (remote + local)
 * - Jika isEditMode == false -> gunakan ImagePreviewAdapter sederhana (local only)
 * - Menghindari upload ganda: pastikan setiap Uri hanya ditambahkan sekali ke pickedUris
 *
 * Catatan:
 * - Penghapusan gambar existing di UI tidak otomatis menghapus asset Cloudinary/Firestore
 *   kecuali repository/backend ditambah fungsionalitasnya.
 */
@AndroidEntryPoint
class AddEditProductFragment : Fragment(R.layout.fragment_add_edit_product) {

    private var _binding: FragmentAddEditProductBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var productRepository: ProductRepository

    // Max images
    private val MAX_IMAGES = 6

    // For existing remote URLs (when editing)
    private val existingImageUrls = mutableListOf<String>()
    private val existingImagePublicIds = mutableListOf<String?>() // parallel to existingImageUrls

    // For newly picked local Uris (sumber kebenaran untuk upload)
    private val pickedUris = mutableListOf<Uri>()

    // Adapters (one will be used depending on mode)
    private var mixedAdapter: MixedImagePreviewAdapter? = null
    private var simpleAdapter: ImagePreviewAdapter? = null

    // productId arg -> if present, we are editing
    private var editingProductId: String? = null
    private var isEditMode = false
    private var loadedProduct: Product? = null



    // modern activity result API
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult

        val totalNow = existingImageUrls.size + pickedUris.size
        val remaining = MAX_IMAGES - totalNow
        if (remaining <= 0) {
            Toast.makeText(requireContext(), "Maksimum $MAX_IMAGES gambar", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val toAdd = uris.take(remaining)

        if (isEditMode) {
            // edit mode:
            // - pickedUris adalah sumber data untuk upload -> tambahkan uri ke pickedUris
            // - mixedAdapter menyimpan item campuran (remote+local) -> tambahkan local ke adapter juga
            toAdd.forEach { uri ->
                if (!pickedUris.contains(uri)) {
                    pickedUris.add(uri)
                    mixedAdapter?.addLocal(uri)
                }
            }
        } else {
            // add mode:
            // - gunakan adapter untuk menambahkan ke list internal adapter yang memang menunjuk ke pickedUris
            // - ImagePreviewAdapter.addUri(...) menambahkan ke list yang sama (pickedUris) sehingga tidak double
            toAdd.forEach { uri ->
                // safety: hindari duplikat
                if (!pickedUris.contains(uri)) {
                    simpleAdapter?.addUri(uri) // addUri akan menambah ke items (yang refer ke pickedUris)
                    // jangan juga memanggil pickedUris.add(uri) di sini -> itu menyebabkan duplikat
                }
            }
        }

        if (uris.size > remaining) {
            Toast.makeText(requireContext(), "Hanya $remaining gambar ditambahkan. Maks $MAX_IMAGES gambar.", Toast.LENGTH_LONG).show()
        }
        updatePickButtonText()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAddEditProductBinding.bind(view)

        // check arg productId (navigate from MyProductsFragment)
        editingProductId = arguments?.getString("productId")
        isEditMode = !editingProductId.isNullOrBlank()

        // headline
        binding.tvHeadline.text = if (isEditMode) "Edit Produk" else "Tambah Produk"

        // setup spinners
        val categories = listOf(
            "Buku","Elektronik","Perlengkapan Laboratorium","Aksesoris","Furniture",
            "Perlengkapan Kelas","Cetakan & Alat Tulis","Pakaian & Sepatu","Peralatan Olahraga",
            "Alat Musik","Peralatan Seni & Kerajinan","Perlengkapan Kos & Dapur",
            "Kamera & Perlengkapan Fotografi","Kendaraan Ringan (Sepeda/Scooter)",
            "Media & Perangkat Lunak Edukasi","Perlengkapan Presentasi","Gadget & Aksesori Mobile","Lainnya"
        )
        binding.spinnerCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spinnerCondition.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Baik","Seperti Baru","Cukup"))
        binding.spinnerAction.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Jual Beli","Barter"))

        // RecyclerView for images: layout manager common
        binding.rvImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvImages.setHasFixedSize(true)

        // init adapter depending on mode
        if (isEditMode) {
            // Edit mode: Mixed adapter
            mixedAdapter = MixedImagePreviewAdapter(
                items = mutableListOf(), // will populate when product loaded
                // inside AddEditProductFragment: replace previous onRemoveRemote body with this safe version
                onRemoveRemote = { idxRemote, url ->
                    // get associated publicId if available (parallel list)
                    val publicId = existingImagePublicIds.getOrNull(idxRemote)

                    // start one-shot coroutine (lifecycleScope). We will guard binding accesses.
                    viewLifecycleOwner.lifecycleScope.launch {
                        // capture current binding reference for a quick pre-check
                        val preBinding = _binding
                        if (preBinding == null) {
                            // view already gone, nothing to do
                            return@launch
                        }

                        // show progress quickly - operate on preBinding (still valid now)
                        preBinding.progressUploading.visibility = View.VISIBLE

                        try {
                            val pid = editingProductId ?: throw IllegalStateException("productId kosong")
                            // network / IO suspend
                            productRepository.removeImageFromProduct(pid, publicId, url)

                            // after suspend, verify view still exists and lifecycle is ok
                            if (_binding == null || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                // view gone - do not touch UI
                                return@launch
                            }

                            // safe to update UI now
                            _binding?.let { b ->
                                // remove from local tracking lists safely by index check
                                if (idxRemote in 0 until existingImageUrls.size) existingImageUrls.removeAt(idxRemote)
                                if (idxRemote in 0 until existingImagePublicIds.size) existingImagePublicIds.removeAt(idxRemote)

                                // update adapter items - MixedImagePreviewAdapter should expose items or helper method
                                mixedAdapter?.let { ma ->
                                    if (idxRemote in 0 until ma.items.size) {
                                        ma.items.removeAt(idxRemote)
                                        ma.notifyDataSetChanged()
                                    } else {
                                        ma.notifyDataSetChanged()
                                    }
                                }

                                Toast.makeText(requireContext(), "Gambar dihapus", Toast.LENGTH_SHORT).show()
                                b.progressUploading.visibility = View.GONE
                                updatePickButtonText()
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            // coroutine dibatalkan - biarkan pembatalan mengalir. jangan update UI.
                            throw ce
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // hanya update UI jika view masih ada
                            _binding?.let { b ->
                                b.progressUploading.visibility = View.GONE
                                Toast.makeText(requireContext(), "Gagal menghapus gambar: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            // final guard: pastikan view masih ada sebelum mengubah UI
                            _binding?.let { b ->
                                b.progressUploading.visibility = View.GONE
                            }
                        }
                    }
                },
                onRemoveLocal = { idxLocal, uri ->
                    // adapter already removed item; remove from pickedUris as well
                    val idx = pickedUris.indexOfFirst { it == uri }
                    if (idx >= 0) pickedUris.removeAt(idx)
                    updatePickButtonText()
                },
                onClickImage = { item ->
                    when (item) {
                        is ImageItem.Remote -> {
                            val intent = ImageViewerActivity.createIntent(requireContext(), item.url)
                            startActivity(intent)
                        }
                        is ImageItem.Local -> {
                            val path = item.uri.toString()
                            val intent = ImageViewerActivity.createIntent(requireContext(), path)
                            startActivity(intent)
                        }
                    }
                }
            )
            binding.rvImages.adapter = mixedAdapter
        } else {
            // Add mode: simple adapter that only shows local Uris
            // ImagePreviewAdapter expects a MutableList<Uri> reference (we pass pickedUris)
            simpleAdapter = ImagePreviewAdapter(pickedUris) { pos ->
                if (pos in 0 until pickedUris.size) {
                    pickedUris.removeAt(pos)
                    simpleAdapter?.notifyItemRemoved(pos)
                    simpleAdapter?.notifyItemRangeChanged(pos, pickedUris.size - pos)
                    updatePickButtonText()
                }
            }
            binding.rvImages.adapter = simpleAdapter
        }

        // pick images
        binding.btnPickImages.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        updatePickButtonText()

        // save action
        binding.btnSaveProduct.setOnClickListener { saveProduct() }

        // if edit mode -> load product data lifecycle-safe
        if (isEditMode) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        val pid = editingProductId ?: return@repeatOnLifecycle
                        val product = productRepository.getProductOnce(pid)
                        product?.let { populateForEdit(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Gagal memuat produk untuk edit: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun populateForEdit(product: Product) {
        loadedProduct = product
        binding.etTitle.setText(product.title)
        binding.etPrice.setText(product.price.toString())
        binding.etDescription.setText(product.description)
        // set spinner selections
        setSpinnerSelection(binding.spinnerCategory, product.category)
        setSpinnerSelection(binding.spinnerCondition, product.condition)
        setSpinnerSelection(binding.spinnerAction, product.action ?: "Jual Beli")

        // existing image urls + publicIds
        existingImageUrls.clear()
        existingImageUrls.addAll(product.imageUrls.filter { it.isNotBlank() && it != "-" })

        existingImagePublicIds.clear()
        // product.imagePublicIds may be null or shorter; align with urls by index when possible
        existingImagePublicIds.addAll(product.imagePublicIds ?: List(existingImageUrls.size) { null })

        // Populate mixed adapter with existing remote urls first
        mixedAdapter?.let { adapter ->
            adapter.items.clear()
            for (i in existingImageUrls.indices) {
                val url = existingImageUrls[i]
                val pubId = existingImagePublicIds.getOrNull(i)
                // we keep adapter representation simple: adapter.addRemote(url) — internal items
                adapter.addRemote(url)
                // if your MixedImagePreviewAdapter supports carrying publicId, prefer adapter.addRemote(url, pubId)
            }
            // newly picked local images will be added to adapter when picked
            adapter.notifyDataSetChanged()
        }

        updatePickButtonText()
    }


    private fun setSpinnerSelection(spinner: android.widget.Spinner, value: String?) {
        if (value.isNullOrBlank()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if ((adapter.getItem(i) as? String) == value) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun updatePickButtonText() {
        val total = if (isEditMode) {
            (existingImageUrls.size + pickedUris.size)
        } else {
            pickedUris.size
        }
        binding.btnPickImages.text = if (total == 0) "Pilih Foto" else "Pilih Foto ($total terpasang)"
    }

    private fun saveProduct() {
        val title = binding.etTitle.text.toString().trim()
        val priceRaw = binding.etPrice.text.toString().trim()
        val desc = binding.etDescription.text.toString().trim()
        val category = binding.spinnerCategory.selectedItem.toString()
        val condition = binding.spinnerCondition.selectedItem.toString()
        val action = binding.spinnerAction.selectedItem.toString()

        if (title.isEmpty()) {
            binding.etTitle.error = "Judul harus diisi"
            return
        }
        val price = priceRaw.toDoubleOrNull() ?: 0.0

        binding.progressUploading.visibility = View.VISIBLE
        binding.progressUploading.progress = 0

        // one-shot coroutine for save
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // convert pickedUris -> Files (compressed)
                val imageFiles = mutableListOf<File>()
                for ((index, uri) in pickedUris.withIndex()) {
                    val name = "product_${System.currentTimeMillis()}_$index.jpg"
                    val f = withContext(Dispatchers.IO) { uriToCompressedFile(uri, name) }
                    f?.let { imageFiles.add(it) }
                }

                if (isEditMode) {
                    val base = loadedProduct ?: throw Exception("Produk tidak dimuat")
                    val updated = base.copy(
                        title = title,
                        description = desc,
                        category = category,
                        condition = condition,
                        price = price,
                        action = action
                    )

                    // NOTE: repository.updateProduct akan mengambil existing imageUrls dari Firestore
                    // dan menambahkan uploaded new ones. Jika ingin persist penghapusan existingImageUrls
                    // kamu perlu memperbarui repository untuk menerima daftar keepImageUrls.
                    productRepository.updateProduct(
                        base.productId,
                        updated,
                        if (imageFiles.isEmpty()) null else imageFiles,
                        "unitrade-products-pictures"
                    )
                    Toast.makeText(requireContext(), "Produk diperbarui", Toast.LENGTH_SHORT).show()
                } else {
                    val product = Product(
                        productId = "",
                        ownerId = "",
                        title = title,
                        description = desc,
                        category = category,
                        condition = condition,
                        price = price,
                        imageUrls = emptyList(),
                        isActive = true,
                        createdAt = null,
                        updatedAt = null,
                        action = action
                    )
                    val savedProductId = productRepository.addProduct(product, imageFiles, "unitrade-products-pictures")
                    Toast.makeText(requireContext(), "Produk tersimpan (id: $savedProductId)", Toast.LENGTH_LONG).show()
                }
                findNavController().popBackStack()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Gagal menyimpan produk: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressUploading.visibility = View.GONE
            }
        }
    }

    // konversi Uri -> File yang dikompresi (disimpan di cache)
    private suspend fun uriToCompressedFile(uri: Uri, outName: String): File? = withContext(Dispatchers.IO) {
        try {
            val bmp = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            val maxDim = 1280
            val (w, h) = bmp.width to bmp.height
            val scale = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
            val scaledBmp = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (w * scale).toInt(), (h * scale).toInt(), true) else bmp

            val outFile = File(requireContext().cacheDir, outName)
            FileOutputStream(outFile).use { fos ->
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                fos.flush()
            }
            outFile
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

