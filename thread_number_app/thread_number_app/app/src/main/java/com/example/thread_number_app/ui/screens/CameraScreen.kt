package com.example.thread_number_app.ui.screens

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.thread_number_app.data.RetrofitClient
import com.example.thread_number_app.ui.navigation.AppScreen
import com.example.thread_number_app.ui.viewmodel.ResultViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    val permissionLauncher =
        rememberLauncherForActivityResult(
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

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->

                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture =
                        ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({

                        val cameraProvider =
                            cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .build()
                            .apply {
                                setSurfaceProvider(
                                    previewView.surfaceProvider
                                )
                            }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            )
                            .setJpegQuality(90)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraX", "Bind failed", e)
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp)
                )
            }

            Button(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                onClick = {

                    val file = createImageFile(context)
                    val options =
                        ImageCapture.OutputFileOptions
                            .Builder(file)
                            .build()

                    loading = true

                    imageCapture?.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {

                            override fun onError(
                                exc: ImageCaptureException
                            ) {
                                loading = false
                                Log.e("CameraX", "Capture failed", exc)
                            }

                            override fun onImageSaved(
                                output: ImageCapture.OutputFileResults
                            ) {

                                scope.launch {

                                    try {

                                        val body =
                                            file.asRequestBody(
                                                "image/jpeg"
                                                    .toMediaTypeOrNull()
                                            )

                                        val multipart =
                                            MultipartBody.Part
                                                .createFormData(
                                                    "file",
                                                    file.name,
                                                    body
                                                )

                                        val res =
                                            RetrofitClient.api
                                                .predict(multipart)

                                        Log.d("API_DEBUG", "Response Camera: $res")
                                        Log.d("API_DEBUG", "Top1 raw: ${res.top1_label}")
                                        Log.d("API_DEBUG", "Top2 raw: ${res.top2_label}")

                                        resultViewModel.setResult(
                                            imagePath =
                                                file.absolutePath,
                                            top1 =
                                                res.top1_label
                                                    ?: "-",
                                            top2 =
                                                res.top2_label
                                                    ?: "-"
                                        )

                                        loading = false

                                        navController.navigate(
                                            AppScreen.Result.route
                                        ) {
                                            launchSingleTop = true
                                        }

                                    } catch (e: Exception) {
                                        loading = false
                                        Log.e(
                                            "UPLOAD",
                                            "Upload error",
                                            e
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            ) {
                Text("Ambil Gambar")
            }
        }
    }
}

// ================= FILE CREATOR =================
private fun createImageFile(context: Context): File {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = context.externalCacheDir ?: context.cacheDir
    return File(dir, "IMG_$ts.jpg")
}

// ================= UPLOAD + SAVE RESULT =================
private suspend fun uploadAndNavigate(
    file: File,
    navController: NavController,
    resultViewModel: ResultViewModel,
    onComplete: () -> Unit
) {
    try {

        val body =
            file.asRequestBody("image/jpeg".toMediaTypeOrNull())

        val multipart =
            MultipartBody.Part.createFormData(
                "file",
                file.name,
                body
            )

        val res = RetrofitClient.api.predict(multipart)


        resultViewModel.setResult(
            imagePath = file.absolutePath,
            top1 = res.top1_label ?: "-",
            top2 = res.top2_label ?: "-"
        )

        navController.navigate(AppScreen.Result.route) {
            launchSingleTop = true
        }

    } catch (e: Exception) {
        Log.e("UPLOAD", "Upload error", e)
    } finally {
        onComplete()
    }
}

