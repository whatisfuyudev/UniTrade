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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.unitrade.unitrade.Product
import com.unitrade.unitrade.ProductRepository
import com.unitrade.unitrade.R
import com.unitrade.unitrade.databinding.FragmentAddEditProductBinding
import com.unitrade.unitrade.ImagePreviewAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * File: app/src/main/java/com/unitrade/unitrade/ui/product/AddEditProductFragment.kt
 *
 * Fragment untuk menambah / mengedit produk
 * - lokasi file: app/src/main/java/com/unitrade/unitrade/ui/product/AddEditProductFragment.kt
 *
 * Yang dilakukan file ini:
 * 1) Menyediakan UI untuk input title, price, description, category, condition, action.
 * 2) Memungkinkan pemilihan multi-image via GetMultipleContents (modern API).
 * 3) Membatasi jumlah gambar maksimal (MAX_IMAGES).
 * 4) Menampilkan preview gambar di RecyclerView horizontal menggunakan ImagePreviewAdapter.
 * 5) Menghapus gambar (cancel) dari preview.
 * 6) Mengkonversi Uri -> File di cache app (kompres sederhana) saat menyimpan, lalu memanggil ProductRepository.addProduct.
 *
 * Catatan:
 * - Pastikan ada layout item_image_preview.xml dengan ImageView id "imgPreview" dan ImageButton id "btnRemove".
 * - Pastikan ImagePreviewAdapter berada di package com.unitrade.unitrade (atau import sesuai lokasi).
 */
@AndroidEntryPoint
class AddEditProductFragment : Fragment(R.layout.fragment_add_edit_product) {

    private var _binding: FragmentAddEditProductBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var productRepository: ProductRepository

    // batas maksimal gambar
    private val MAX_IMAGES = 6

    // backing list untuk uris yang dipilih
    private val pickedUris = mutableListOf<Uri>()

    // adapter preview (menggunakan file ImagePreviewAdapter.kt yang kamu kirim)
    private lateinit var imageAdapter: com.unitrade.unitrade.ImagePreviewAdapter

    // modern activity result API: dapatkan multiple content (list<Uri>)
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult

        // hanya tambahkan sebanyak slot yang tersedia
        val remaining = MAX_IMAGES - pickedUris.size
        if (remaining <= 0) {
            Toast.makeText(requireContext(), "Maksimum $MAX_IMAGES gambar", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        // ambil uris pertama sampai batas remaining
        val toAdd = uris.take(remaining)
        var added = 0
        for (u in toAdd) {
            if (!pickedUris.contains(u)) {
                pickedUris.add(u)
                added++
            }
        }

        if (uris.size > remaining) {
            Toast.makeText(requireContext(), "Hanya $remaining gambar ditambahkan. Maks $MAX_IMAGES gambar.", Toast.LENGTH_LONG).show()
        }

        // beri tahu adapter dan update tombol
        imageAdapter.notifyDataSetChanged()
        updatePickButtonText()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAddEditProductBinding.bind(view)

        // --- setup spinners ---
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

        // --- RecyclerView images: pastikan LayoutManager horizontal dipasang ---
        binding.rvImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvImages.setHasFixedSize(true)

        imageAdapter = com.unitrade.unitrade.ImagePreviewAdapter(pickedUris) { pos ->
            // callback hapus dari adapter
            if (pos in 0 until pickedUris.size) {
                pickedUris.removeAt(pos)
                imageAdapter.notifyItemRemoved(pos)
                imageAdapter.notifyItemRangeChanged(pos, pickedUris.size - pos)
                updatePickButtonText()
            }
        }

        binding.rvImages.adapter = imageAdapter

        // tombol pick images
        binding.btnPickImages.setOnClickListener {
            // buka picker; user bisa memilih lebih banyak, tapi fragment akan membatasi saat hasil kembali
            pickImagesLauncher.launch("image/*")
        }

        updatePickButtonText()

        // simpan produk
        binding.btnSaveProduct.setOnClickListener { saveProduct() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updatePickButtonText() {
        val count = pickedUris.size
        binding.btnPickImages.text = if (count == 0) "Pilih Foto" else "Pilih Foto ($count terpilih)"
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

        // coroutine untuk compress + upload via repository
        lifecycleScope.launchWhenStarted {
            try {
                // convert Uri -> File (cache)
                val imageFiles = mutableListOf<File>()
                for ((index, uri) in pickedUris.withIndex()) {
                    val f = withContext(Dispatchers.IO) { uriToCompressedFile(uri, "product_${System.currentTimeMillis()}_$index.jpg") }
                    f?.let { imageFiles.add(it) }
                }

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

                // folder 'unitrade-products-pictures' diteruskan ke repository
                val savedProductId = productRepository.addProduct(product, imageFiles, "unitrade-products-pictures")
                Toast.makeText(requireContext(), "Produk tersimpan (id: $savedProductId)", Toast.LENGTH_LONG).show()
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
}
