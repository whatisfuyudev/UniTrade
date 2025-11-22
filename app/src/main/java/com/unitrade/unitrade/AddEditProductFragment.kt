package com.unitrade.unitrade

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.unitrade.unitrade.R
import com.unitrade.unitrade.Product
import com.unitrade.unitrade.ProductRepository
import com.unitrade.unitrade.databinding.FragmentAddEditProductBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

/**
 * File: app/src/main/java/com/unitrade/unitrade/ui/product/AddEditProductFragment.kt
 *
 * Fragment untuk menambah (atau mengedit) produk.
 * - Letak file: app/src/main/java/com/unitrade/unitrade/ui/product/AddEditProductFragment.kt
 *
 * Fungsionalitas utama:
 * 1. Pilih banyak gambar (ACTION_GET_CONTENT) dari gallery.
 * 2. Preview gambar yang dipilih menggunakan RecyclerView (adapter disediakan minimal).
 * 3. Compress & konversi Uri -> File di cache app secara asynchronous.
 * 4. Upload gambar ke Cloudinary (menggunakan CloudinaryUploader) ke folder
 *    "unitrade-products-pictures" melalui ProductRepository.
 * 5. Simpan metadata produk ke Firestore (productId di-generate oleh Firestore).
 *
 * Catatan keamanan:
 * - Untuk production lebih baik gunakan server-side signed upload ke Cloudinary.
 * - Saat ini contoh menggunakan unsigned preset; lihat instruksi di bagian akhir.
 */

@AndroidEntryPoint
class AddEditProductFragment : Fragment(R.layout.fragment_add_edit_product) {

    private var _binding: FragmentAddEditProductBinding? = null
    private val binding get() = _binding!!

    // repository bertanggung jawab menyimpan data ke Firestore dan upload gambar
    @Inject
    lateinit var productRepository: ProductRepository

    private val PICK_IMAGES_REQUEST = 1001
    private val pickedUris = mutableListOf<Uri>()
    private lateinit var imageAdapter: ImagePreviewAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAddEditProductBinding.bind(view)

        // spinners: categories, conditions, action
        val categories = listOf(
            "Buku","Elektronik","Perlengkapan Laboratorium","Aksesoris","Furniture",
            "Perlengkapan Kelas","Cetakan & Alat Tulis","Pakaian & Sepatu","Peralatan Olahraga",
            "Alat Musik","Peralatan Seni & Kerajinan","Perlengkapan Kos & Dapur",
            "Kamera & Perlengkapan Fotografi","Kendaraan Ringan (Sepeda/Scooter)",
            "Media & Perangkat Lunak Edukasi","Perlengkapan Presentasi","Gadget & Aksesori Mobile","Lainnya"
        )
        binding.spinnerCategory.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        val conditions = listOf("Baik", "Seperti Baru", "Cukup")
        binding.spinnerCondition.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, conditions)

        val actions = listOf("Jual Beli", "Barter")
        binding.spinnerAction.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, actions)

        // images RecyclerView adapter
        imageAdapter = ImagePreviewAdapter(pickedUris) { position ->
            // remove image callback
            pickedUris.removeAt(position)
            imageAdapter.notifyItemRemoved(position)
        }
        binding.rvImages.adapter = imageAdapter
        binding.rvImages.setHasFixedSize(true)

        binding.btnPickImages.setOnClickListener {
            pickImages()
        }

        binding.btnSaveProduct.setOnClickListener {
            saveProduct()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // open system picker for multiple images
    private fun pickImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Pilih foto produk (maks 6)"), PICK_IMAGES_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            // clear earlier selection if you want; here append but respect max 6
            if (data.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    if (pickedUris.size < 6) pickedUris.add(uri)
                }
            } else {
                data.data?.let { uri ->
                    if (pickedUris.size < 6) pickedUris.add(uri)
                }
            }
            imageAdapter.notifyDataSetChanged()
        }
    }

    // main save flow: compress & convert URIs to files, upload via repository, save product metadata
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

        // show progress
        binding.progressUploading.visibility = View.VISIBLE
        binding.progressUploading.progress = 0

        lifecycleScope.launch {
            try {
                // 1) convert Uri -> compressed File in cache dir
                val imageFiles = mutableListOf<File>()
                for ((index, uri) in pickedUris.withIndex()) {
                    val f = withContext(Dispatchers.IO) { uriToCompressedFile(uri, "product_${System.currentTimeMillis()}_$index.jpg") }
                    f?.let { imageFiles.add(it) }
                }

                // 2) create product object (productId assigned inside repository)
                val product = Product(
                    productId = "", // repository will generate id on Firestore
                    ownerId = "", // repository will set current user id
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

                // 3) Save via repository (uploads images to cloudinary and writes doc)
                val savedProductId = productRepository.addProduct(product, imageFiles, "unitrade-products-pictures") // folder param

                // 4) success
                Toast.makeText(requireContext(), "Produk tersimpan (id: $savedProductId)", Toast.LENGTH_LONG).show()
                // navigate back to home or product detail
                findNavController().popBackStack()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Gagal menyimpan produk: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressUploading.visibility = View.GONE
            }
        }
    }

    // Convert Uri to compressed JPEG File stored in cache dir
    private suspend fun uriToCompressedFile(uri: Uri, outName: String): File? = withContext(Dispatchers.IO) {
        try {
            // read bitmap
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@withContext null
            val bmp = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            // simple resize: limit max dimension to 1280 px
            val maxDim = 1280
            val (w, h) = bmp.width to bmp.height
            val scale = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
            val scaledBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (w * scale).toInt(), (h * scale).toInt(), true)
            } else bmp

            val outFile = File(requireContext().cacheDir, outName)
            FileOutputStream(outFile).use { fos ->
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, 80, fos) // quality 80
                fos.flush()
            }
            outFile
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
