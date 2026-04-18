package com.example.thread_number_app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.thread_number_app.data.RetrofitClient
import com.example.thread_number_app.ui.navigation.AppScreen
import com.example.thread_number_app.ui.utils.uriToFile
import com.example.thread_number_app.ui.viewmodel.ResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

// Konstanta kompresi
private const val GALLERY_TARGET_KB    = 400
private const val GALLERY_TOLERANCE_KB = 30
private const val GALLERY_MAX_ITER     = 20

@Composable
fun GalleryPredictionScreen(
    navController  : NavController,
    imagePath      : String,
    resultViewModel: ResultViewModel
) {
    val context = LocalContext.current

    var loading       by remember { mutableStateOf(true) }
    var error         by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Memuat gambar...") }

    LaunchedEffect(imagePath) {
        try {
            // 1. URI → File cache
            statusMessage = "Memuat gambar..."
            val uri  = Uri.parse(imagePath)
            val file = uriToFile(context, uri)

            val originalSizeKB = file.length() / 1024
            Log.d("GALLERY", "Ukuran file asli: ${originalSizeKB} KB")

            // 2. Kompresi jika ukuran melebihi batas
            val requestBody = if (originalSizeKB > GALLERY_TARGET_KB) {
                statusMessage = "Mengompresi gambar..."
                Log.d("GALLERY", "Ukuran melebihi ${GALLERY_TARGET_KB} KB, mulai kompresi...")

                val compressed = compressGalleryImage(
                    filePath    = file.absolutePath,
                    targetKB    = GALLERY_TARGET_KB,
                    toleranceKB = GALLERY_TOLERANCE_KB
                )

                Log.d(
                    "GALLERY",
                    "Kompresi selesai: ${compressed.size / 1024} KB " +
                            "(target: ${GALLERY_TARGET_KB} KB ±${GALLERY_TOLERANCE_KB} KB)"
                )

                compressed.toRequestBody("image/jpeg".toMediaTypeOrNull())

            } else {
                // Ukuran sudah di bawah target — kirim langsung tanpa kompresi
                Log.d("GALLERY", "Ukuran ${originalSizeKB} KB, tidak perlu kompresi")
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            }

            // 3. Kirim ke server 
            statusMessage = "Mengirim gambar ke server..."
            val multipart = MultipartBody.Part.createFormData(
                name     = "file",
                filename = file.name,
                body     = requestBody
            )

            val res = RetrofitClient.api.predict(multipart)

            Log.d("API_DEBUG", "Top1: ${res.top1_label}")
            Log.d("API_DEBUG", "Top2: ${res.top2_label}")

            // 4. Simpan hasil dan navigasi 
            resultViewModel.setResult(
                imagePath = imagePath,   // URI asli dipertahankan untuk halaman result
                top1      = res.top1_label ?: "-",
                top2      = res.top2_label ?: "-"
            )

            navController.navigate(AppScreen.Result.route) {
                launchSingleTop = true
                popUpTo("main")
            }

        } catch (e: Exception) {
            error = e.message
            Log.e("GALLERY", "Gagal memproses gambar: ${e.message}", e)
        } finally {
            loading = false
        }
    }

    // UI 
    Box(
        modifier        = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(statusMessage)
                }
            }
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text  = "Terjadi kesalahan",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = error ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// Kompres gambar galeri
private suspend fun compressGalleryImage(
    filePath    : String,
    targetKB    : Int,
    toleranceKB : Int
): ByteArray = withContext(Dispatchers.IO) {

    val bitmap = BitmapFactory.decodeFile(filePath)
        ?: throw IllegalStateException("Gagal decode bitmap dari: $filePath")

    val lowerBound = (targetKB - toleranceKB) * 1024
    val upperBound = (targetKB + toleranceKB) * 1024

    var lo      = 50
    var hi      = 95
    var quality = 85
    var result  = ByteArray(0)

    repeat(GALLERY_MAX_ITER) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        result = stream.toByteArray()

        when {
            result.size < lowerBound -> {
                lo      = quality + 1
                quality = (lo + hi) / 2
            }
            result.size > upperBound -> {
                hi      = quality - 1
                quality = (lo + hi) / 2
            }
            else -> return@repeat
        }

        if (lo > hi) return@repeat
    }

    Log.d(
        "GALLERY",
        "Kualitas JPEG akhir: $quality, " +
                "ukuran: ${result.size / 1024} KB"
    )

    result
}