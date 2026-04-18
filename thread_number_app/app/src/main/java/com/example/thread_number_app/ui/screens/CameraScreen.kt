package com.example.thread_number_app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Threshold ketajaman (Laplacian variance)
private const val BLUR_MIN = 30.0
private const val BLUR_MAX = 400.0

// Target ukuran file sebelum dikirim ke server
private const val TARGET_KB       = 400
private const val TOLERANCE_KB    = 30   // toleransi ±30 KB → 370–430 KB dianggap pas
private const val MAX_ITERATIONS  = 20   // batas iterasi agar tidak infinite loop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    resultViewModel: ResultViewModel
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    var imageCapture   by remember { mutableStateOf<ImageCapture?>(null) }
    val camera          = remember { mutableStateOf<Camera?>(null) }
    var loading         by remember { mutableStateOf(false) }

    var sharpness       by remember { mutableStateOf(0.0) }
    var distanceStatus  by remember { mutableStateOf(DistanceStatus.UNKNOWN) }

    var focusPoint      by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing   by remember { mutableStateOf(false) }
    val previewViewRef  = remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val statusColor = when (distanceStatus) {
        DistanceStatus.IDEAL     -> Color.Green
        DistanceStatus.TOO_CLOSE -> Color(0xFFFF9800)
        DistanceStatus.TOO_FAR   -> Color.Red
        DistanceStatus.UNKNOWN   -> Color.LightGray
    }

    val guideText = when (distanceStatus) {
        DistanceStatus.IDEAL     -> "✓ Jarak ideal — Siap ambil gambar"
        DistanceStatus.TOO_CLOSE -> "↑ Terlalu dekat — Jauhkan kamera dari kain/tap layar untuk fokus"
        DistanceStatus.TOO_FAR   -> "↓ Terlalu jauh — Dekatkan kamera ke kain"
        DistanceStatus.UNKNOWN   -> "Arahkan kamera ke kain..."
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ambil Gambar Kain") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // Camera Preview
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory  = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    previewViewRef.value = previewView

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(340, 240))
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                        imageAnalysis.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx)
                        ) { imageProxy ->
                            scope.launch {
                                val variance = computeLaplacianVariance(imageProxy)
                                imageProxy.close()
                                sharpness      = variance
                                distanceStatus = when {
                                    variance < 1.0       -> DistanceStatus.UNKNOWN
                                    variance < BLUR_MIN  -> DistanceStatus.TOO_CLOSE
                                    variance <= BLUR_MAX -> DistanceStatus.IDEAL
                                    else                 -> DistanceStatus.TOO_FAR
                                }
                            }
                        }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setJpegQuality(95)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                            camera.value = cam

                            // Tap-to-focus via native touch listener 
                            previewView.setOnTouchListener { _, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val factory = previewView.meteringPointFactory
                                    val point   = factory.createPoint(event.x, event.y)
                                    val action  = FocusMeteringAction.Builder(
                                        point,
                                        FocusMeteringAction.FLAG_AF or
                                                FocusMeteringAction.FLAG_AE
                                    )
                                        .setAutoCancelDuration(
                                            3, java.util.concurrent.TimeUnit.SECONDS
                                        )
                                        .build()

                                    cam.cameraControl.startFocusAndMetering(action)

                                    focusPoint    = Offset(event.x, event.y)
                                    showFocusRing = true
                                    scope.launch {
                                        delay(800)
                                        showFocusRing = false
                                    }
                                }
                                true
                            }

                        } catch (e: Exception) {
                            Log.e("CameraX", "Bind failed", e)
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Overlay: lingkaran panduan + focus ring 
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r  = size.width * 0.38f

                drawCircle(
                    color  = statusColor,
                    center = Offset(cx, cy),
                    radius = r,
                    style  = Stroke(width = 3.dp.toPx())
                )

                val cs = 18.dp.toPx()
                drawLine(statusColor, Offset(cx - cs, cy), Offset(cx + cs, cy), 1.5.dp.toPx())
                drawLine(statusColor, Offset(cx, cy - cs), Offset(cx, cy + cs), 1.5.dp.toPx())

                if (showFocusRing && focusPoint != null) {
                    val fp   = focusPoint!!
                    val rs   = 60.dp.toPx()
                    val cl   = 14.dp.toPx()
                    val l    = fp.x - rs / 2
                    val t    = fp.y - rs / 2
                    val ri   = fp.x + rs / 2
                    val b    = fp.y + rs / 2

                    drawRect(
                        color   = Color.Yellow,
                        topLeft = Offset(l, t),
                        size    = Size(rs, rs),
                        style   = Stroke(width = 2.dp.toPx())
                    )
                    // Corner brackets
                    drawLine(Color.Yellow, Offset(l, t + cl), Offset(l, t), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(l, t), Offset(l + cl, t), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(ri - cl, t), Offset(ri, t), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(ri, t), Offset(ri, t + cl), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(l, b - cl), Offset(l, b), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(l, b), Offset(l + cl, b), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(ri - cl, b), Offset(ri, b), 3.dp.toPx())
                    drawLine(Color.Yellow, Offset(ri, b), Offset(ri, b - cl), 3.dp.toPx())
                }
            }

            // Panel indikator atas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(color = statusColor)
                        }
                        Text(
                            text = if (sharpness > 0)
                                "Ketajaman: ${"%.1f".format(sharpness)}"
                            else "Mendeteksi...",
                            color    = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black) {
                    Text(
                        text       = guideText,
                        color      = statusColor,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.padding(
                            horizontal = 16.dp, vertical = 6.dp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black) {
                    Text(
                        text     = "Tap layar untuk fokus",
                        color    = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Panel bawah: bar jarak + tombol 
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DistanceBar(sharpness = sharpness, status = distanceStatus)

                Spacer(modifier = Modifier.height(12.dp))

                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(bottom = 8.dp),
                        color    = Color.White
                    )
                    Text(
                        text     = "Mengirim gambar ke server...",
                        color    = Color.White,
                        fontSize = 12.sp
                    )
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = distanceStatus == DistanceStatus.IDEAL,
                        onClick  = {
                            val file    = createImageFile(context)
                            val options = ImageCapture.OutputFileOptions
                                .Builder(file).build()
                            loading = true

                            imageCapture?.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {

                                    override fun onError(exc: ImageCaptureException) {
                                        loading = false
                                        Log.e("CameraX", "Gagal mengambil gambar", exc)
                                    }

                                    override fun onImageSaved(
                                        output: ImageCapture.OutputFileResults
                                    ) {
                                        scope.launch {
                                            try {
                                                // Kompres ke ~400 KB tanpa resize 
                                                val compressed = compressToTargetKB(
                                                    filePath   = file.absolutePath,
                                                    targetKB   = TARGET_KB,
                                                    toleranceKB = TOLERANCE_KB
                                                )

                                                Log.d(
                                                    "COMPRESS",
                                                    "Ukuran setelah kompres: " +
                                                            "${compressed.size / 1024} KB, " +
                                                            "dimensi asli dipertahankan"
                                                )

                                                // Kirim byte array langsung
                                                val requestBody = compressed
                                                    .toRequestBody(
                                                        "image/jpeg".toMediaTypeOrNull()
                                                    )
                                                val multipart =
                                                    MultipartBody.Part.createFormData(
                                                        "file",
                                                        file.name,
                                                        requestBody
                                                    )

                                                val res = RetrofitClient.api
                                                    .predict(multipart)

                                                Log.d("API_DEBUG", "Top1: ${res.top1_label}")
                                                Log.d("API_DEBUG", "Top2: ${res.top2_label}")

                                                resultViewModel.setResult(
                                                    imagePath = file.absolutePath,
                                                    top1      = res.top1_label ?: "-",
                                                    top2      = res.top2_label ?: "-"
                                                )

                                                loading = false
                                                navController.navigate(
                                                    AppScreen.Result.route
                                                ) {
                                                    launchSingleTop = true
                                                }

                                            } catch (e: Exception) {
                                                loading = false
                                                Log.e("UPLOAD", "Upload gagal", e)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    ) {
                        Text(
                            text = if (distanceStatus == DistanceStatus.IDEAL)
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

// Kompres citra ke mendekati TARGET_KB tanpa mengubah dimensi piksel
// Pendekatan yang digunakan (quality-only compression):
//   - Bitmap di-decode dari file (dimensi penuh dipertahankan).
//   - Bitmap di-encode ulang ke JPEG dengan kualitas yang diturunkan
//     secara iteratif sampai ukuran mendekati target.
//   - Perubahan nilai piksel akibat kompresi JPEG sangat kecil pada
//     kualitas ≥ 60 dan tidak signifikan terhadap rata-rata per kanal.
//   - Hasilnya dikembalikan sebagai ByteArray untuk dikirim langsung
//     ke server tanpa perlu menulis file baru ke disk.
private suspend fun compressToTargetKB(
    filePath    : String,
    targetKB    : Int,
    toleranceKB : Int
): ByteArray = withContext(Dispatchers.IO) {

    val bitmap  = BitmapFactory.decodeFile(filePath)
        ?: throw IllegalStateException("Gagal decode bitmap dari: $filePath")

    val lowerBound = (targetKB - toleranceKB) * 1024   // byte
    val upperBound = (targetKB + toleranceKB) * 1024   // byte

    var lo      = 50    // kualitas minimum yang masih aman untuk warna
    var hi      = 95    // kualitas maksimum (95 = hampir lossless)
    var quality = 85    // titik awal
    var result  = ByteArray(0)

    repeat(MAX_ITERATIONS) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        result = stream.toByteArray()

        when {
            result.size < lowerBound -> {
                // Ukuran terlalu kecil → naikkan kualitas
                lo      = quality + 1
                quality = (lo + hi) / 2
            }
            result.size > upperBound -> {
                // Ukuran terlalu besar → turunkan kualitas
                hi      = quality - 1
                quality = (lo + hi) / 2
            }
            else -> {
                // Sudah dalam toleransi → hentikan iterasi
                return@repeat
            }
        }

        // Jika lo > hi, tidak ada solusi yang memenuhi — pakai hasil terakhir
        if (lo > hi) return@repeat
    }

    Log.d(
        "COMPRESS",
        "Kualitas JPEG akhir: $quality, " +
                "ukuran: ${result.size / 1024} KB, " +
                "target: $targetKB KB ±$toleranceKB KB"
    )

    result
}

// Enum status estimasi jarak
enum class DistanceStatus {
    UNKNOWN,
    TOO_CLOSE,
    IDEAL,
    TOO_FAR
}

// Bar visual estimasi jarak
@Composable
private fun DistanceBar(sharpness: Double, status: DistanceStatus) {
    val clamped  = sharpness.coerceIn(0.0, BLUR_MAX + 100.0)
    val progress = (clamped / (BLUR_MAX + 100.0)).toFloat()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dekat", color = Color.LightGray, fontSize = 10.sp)
            Text(
                text = when (status) {
                    DistanceStatus.IDEAL     -> "Jarak Ideal"
                    DistanceStatus.TOO_CLOSE -> "Terlalu Dekat"
                    DistanceStatus.TOO_FAR   -> "Terlalu Jauh"
                    DistanceStatus.UNKNOWN   -> "Mendeteksi..."
                },
                color = when (status) {
                    DistanceStatus.IDEAL     -> Color.Green
                    DistanceStatus.TOO_CLOSE -> Color(0xFFFF9800)
                    DistanceStatus.TOO_FAR   -> Color.Red
                    DistanceStatus.UNKNOWN   -> Color.LightGray
                },
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text("Jauh", color = Color.LightGray, fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val w  = size.width
            val h  = size.height
            val rx = h / 2
            val is_ = (BLUR_MIN / (BLUR_MAX + 100.0)).toFloat() * w
            val ie  = (BLUR_MAX / (BLUR_MAX + 100.0)).toFloat() * w

            drawRoundRect(
                color        = Color(0x66F44336),
                size         = androidx.compose.ui.geometry.Size(is_, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rx)
            )
            drawRect(
                color   = Color(0x664CAF50),
                topLeft = Offset(is_, 0f),
                size    = androidx.compose.ui.geometry.Size(ie - is_, h)
            )
            drawRect(
                color   = Color(0x66F44336),
                topLeft = Offset(ie, 0f),
                size    = androidx.compose.ui.geometry.Size(w - ie, h)
            )

            val ix = (progress * w).coerceIn(rx, w - rx)
            drawCircle(
                color  = when (status) {
                    DistanceStatus.IDEAL     -> Color.Green
                    DistanceStatus.TOO_CLOSE -> Color(0xFFFF9800)
                    DistanceStatus.TOO_FAR   -> Color.Red
                    DistanceStatus.UNKNOWN   -> Color.LightGray
                },
                radius = h,
                center = Offset(ix, h / 2)
            )
        }
    }
}

// Hitung Laplacian Variance sebagai proxy ketajaman gambar 
private suspend fun computeLaplacianVariance(imageProxy: ImageProxy): Double =
    withContext(Dispatchers.Default) {
        try {
            val bitmap = imageProxy.toBitmap()
            val gray   = toGrayscale(bitmap)
            val w      = gray.width
            val h      = gray.height
            val pixels = IntArray(w * h)
            gray.getPixels(pixels, 0, w, 0, 0, w, h)

            var sum   = 0.0
            var sumSq = 0.0
            var count = 0

            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c  = (pixels[y * w + x] and 0xFF).toDouble()
                    val t  = (pixels[(y - 1) * w + x] and 0xFF).toDouble()
                    val bo = (pixels[(y + 1) * w + x] and 0xFF).toDouble()
                    val l  = (pixels[y * w + (x - 1)] and 0xFF).toDouble()
                    val r  = (pixels[y * w + (x + 1)] and 0xFF).toDouble()
                    val lp = t + bo + l + r - 4 * c
                    sum   += lp
                    sumSq += lp * lp
                    count++
                }
            }

            if (count == 0) return@withContext 0.0
            val mean = sum / count
            (sumSq / count) - (mean * mean)

        } catch (e: Exception) {
            0.0
        }
    }

// Konversi Bitmap ke grayscale
private fun toGrayscale(src: Bitmap): Bitmap {
    val dst    = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(dst)
    val paint  = android.graphics.Paint()
    val matrix = android.graphics.ColorMatrix().also { it.setSaturation(0f) }
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return dst
}

// Buat file gambar sementara
private fun createImageFile(context: Context): File {
    val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = context.externalCacheDir ?: context.cacheDir
    return File(dir, "IMG_$ts.jpg")
}