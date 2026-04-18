package com.example.thread_number_app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.thread_number_app.data.RetrofitClient
import com.example.thread_number_app.ui.navigation.AppScreen
import com.example.thread_number_app.ui.viewmodel.ResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Target ukuran citra dalam KB
private const val TARGET_SIZE_KB = 444
// toleransi ±50 KB
private const val SIZE_TOLERANCE_KB = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    resultViewModel: ResultViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var loading by remember { mutableStateOf(false) }

    // State untuk indikator ukuran real-time
    var estimatedSizeKB by remember { mutableStateOf(0L) }
    var sizeStatus by remember { mutableStateOf(SizeStatus.UNKNOWN) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Ambil Gambar Kain") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Camera Preview
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // ImageAnalysis untuk estimasi ukuran real-time
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx)
                        ) { imageProxy ->
                            scope.launch {
                                val sizeKB = estimateFileSizeKB(imageProxy)
                                estimatedSizeKB = sizeKB
                                sizeStatus = when {
                                    sizeKB in (TARGET_SIZE_KB - SIZE_TOLERANCE_KB)..(TARGET_SIZE_KB + SIZE_TOLERANCE_KB) ->
                                        SizeStatus.IDEAL
                                    sizeKB < TARGET_SIZE_KB - SIZE_TOLERANCE_KB ->
                                        SizeStatus.TOO_CLOSE
                                    else -> SizeStatus.TOO_FAR
                                }
                                imageProxy.close()
                            }
                        }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setJpegQuality(90)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraX", "Bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Overlay Lingkaran panduan target + indikator
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius  = size.width * 0.38f

                // Warna lingkaran sesuai status
                val circleColor = when (sizeStatus) {
                    SizeStatus.IDEAL        -> Color(0xFF4CAF50) // hijau
                    SizeStatus.TOO_CLOSE    -> Color(0xFFFF9800) // oranye
                    SizeStatus.TOO_FAR      -> Color(0xFFF44336) // merah
                    SizeStatus.UNKNOWN      -> Color(0xFFFFFFFF) // putih
                }

                // Lingkaran panduan
                drawCircle(
                    color = circleColor,
                    center = Offset(centerX, centerY),
                    radius = radius,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Garis crosshair kecil di tengah
                val crossSize = 18.dp.toPx()
                drawLine(
                    color = circleColor,
                    start = Offset(centerX - crossSize, centerY),
                    end   = Offset(centerX + crossSize, centerY),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = circleColor,
                    start = Offset(centerX, centerY - crossSize),
                    end   = Offset(centerX, centerY + crossSize),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Panel indikator atas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indikator ukuran citra real-time
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC000000)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dot status
                        val dotColor = when (sizeStatus) {
                            SizeStatus.IDEAL    -> Color(0xFF4CAF50)
                            SizeStatus.TOO_CLOSE  -> Color(0xFFFF9800)
                            SizeStatus.TOO_FAR    -> Color(0xFFF44336)
                            SizeStatus.UNKNOWN  -> Color(0xFFAAAAAA)
                        }
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(color = dotColor)
                        }
                        Text(
                            text = if (estimatedSizeKB > 0)
                                "Estimasi ukuran: ${estimatedSizeKB} KB"
                            else
                                "Menghitung ukuran...",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Panduan teks jarak
                val guideText = when (sizeStatus) {
                    SizeStatus.IDEAL ->
                        "✓ Jarak ideal — siap ambil gambar"
                    SizeStatus.TOO_CLOSE ->
                        "↑ Terlalu dekat — jauhkan kamera dari kain"
                    SizeStatus.TOO_FAR ->
                        "↓ Terlalu jauh — dekatkan kamera ke kain"
                    SizeStatus.UNKNOWN ->
                        "Arahkan kamera ke kain..."
                }

                val guideColor = when (sizeStatus) {
                    SizeStatus.IDEAL    -> Color(0xFF4CAF50)
                    SizeStatus.TOO_CLOSE  -> Color(0xFFFF9800)
                    SizeStatus.TOO_FAR    -> Color(0xFFF44336)
                    SizeStatus.UNKNOWN  -> Color.White
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC000000)
                ) {
                    Text(
                        text = guideText,
                        color = guideColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // loading + tombol
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Panduan jarak referensi
                Text(
                    text = "Target: ${TARGET_SIZE_KB} KB  |  Toleransi: ±${SIZE_TOLERANCE_KB} KB",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = Color.White
                    )
                    Text(
                        text = "Mengirim gambar ke server...",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sizeStatus == SizeStatus.IDEAL,
                        onClick = {
                            val file = createImageFile(context)
                            val options = ImageCapture.OutputFileOptions.Builder(file).build()
                            loading = true

                            imageCapture?.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onError(exc: ImageCaptureException) {
                                        loading = false
                                        Log.e("CameraX", "Gagal Menagambil Gambar", exc)
                                    }

                                    override fun onImageSaved(
                                        output: ImageCapture.OutputFileResults
                                    ) {
                                        scope.launch {
                                            try {
                                                // Kompres mendekati target sebelum upload
                                                val compressedFile = compressToTargetSize(
                                                    file, TARGET_SIZE_KB
                                                )

                                                val body = compressedFile.asRequestBody(
                                                    "image/jpeg".toMediaTypeOrNull()
                                                )
                                                val multipart = MultipartBody.Part.createFormData(
                                                    "file", compressedFile.name, body
                                                )

                                                val res = RetrofitClient.api.predict(multipart)

                                                Log.d("API_DEBUG", "Top1: ${res.top1_label}")
                                                Log.d("API_DEBUG", "Top2: ${res.top2_label}")

                                                resultViewModel.setResult(
                                                    imagePath = file.absolutePath,
                                                    top1 = res.top1_label ?: "-",
                                                    top2 = res.top2_label ?: "-"
                                                )

                                                loading = false
                                                navController.navigate(AppScreen.Result.route) {
                                                    launchSingleTop = true
                                                }
                                            } catch (e: Exception) {
                                                loading = false
                                                Log.e("UPLOAD", "Upload Gagal", e)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    ) {
                        Text(
                            text = if (sizeStatus == SizeStatus.IDEAL)
                                "Ambil Gambar"
                            else
                                "Sesuaikan jarak kamera terlebih dahulu"
                        )
                    }
                }
            }
        }
    }
}

// Enum status jarak
enum class SizeStatus {
    UNKNOWN,
    TOO_CLOSE,   // file terlalu kecil → kamera terlalu dekat → gambar terlalu blur/close
    IDEAL,       // ukuran file mendekati target
    TOO_FAR      // file terlalu besar → kamera terlalu jauh → detail terlalu luas
}

// Estimasi ukuran citra
private suspend fun estimateFileSizeKB(imageProxy: ImageProxy): Long =
    withContext(Dispatchers.Default) {
        try {
            val bitmap = imageProxy.toBitmap()
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.size().toLong() / 1024L
        } catch (e: Exception) {
            0L
        }
    }

// Kompres citra mendekati target ukuran sebelum upload
private suspend fun compressToTargetSize(
    originalFile: File,
    targetKB: Int
): File = withContext(Dispatchers.IO) {
    val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
    var quality = 90
    var result: ByteArray

    // Binary search kualitas JPEG agar mendekati target
    do {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        result = stream.toByteArray()
        val sizeKB = result.size / 1024

        when {
            sizeKB > targetKB + 50 -> quality -= 5
            sizeKB < targetKB - 50 -> quality += 5
            else -> break
        }
        quality = quality.coerceIn(10, 95)
    } while (true)

    // Simpan ke file baru
    val compressedFile = File(
        originalFile.parent,
        "compressed_${originalFile.name}"
    )
    FileOutputStream(compressedFile).use { it.write(result) }
    compressedFile
}

// Buat file citra sementara
private fun createImageFile(context: Context): File {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = context.externalCacheDir ?: context.cacheDir
    return File(dir, "IMG_$ts.jpg")
}