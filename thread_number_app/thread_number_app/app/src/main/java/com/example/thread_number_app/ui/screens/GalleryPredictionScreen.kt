package com.example.thread_number_app.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.thread_number_app.data.RetrofitClient
import com.example.thread_number_app.ui.navigation.AppScreen
import com.example.thread_number_app.ui.utils.uriToFile
import com.example.thread_number_app.ui.viewmodel.ResultViewModel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

@Composable
fun GalleryPredictionScreen(
    navController: NavController,
    imagePath: String,
    resultViewModel: ResultViewModel
) {
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imagePath) {

        try {

            // ================= URI → FILE CACHE =================
            val uri = Uri.parse(imagePath)
            val file = uriToFile(context, uri)

            // ================= MULTIPART =================
            val body =
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())

            val multipart =
                MultipartBody.Part.createFormData(
                    name = "file",
                    filename = file.name,
                    body = body
                )

            // ================= API CALL =================
            val res =
                RetrofitClient.api.predict(multipart)

            Log.d("API_DEBUG", "Response Camera: $res")
            Log.d("API_DEBUG", "Top1 raw: ${res.top1_label}")
            Log.d("API_DEBUG", "Top2 raw: ${res.top2_label}")

            // ================= SIMPAN KE VIEWMODEL =================
            // PENTING: simpan URI asli (bukan cache file)
            resultViewModel.setResult(
                imagePath = imagePath,
                top1 = res.top1_label ?: "-",
                top2 = res.top2_label ?: "-"
            )

            // ================= NAVIGASI =================
            navController.navigate(AppScreen.Result.route) {
                launchSingleTop = true
                popUpTo("main")
            }

        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    // ================= UI =================
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        when {
            loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Mengirim gambar ke server...")
                }
            }

            error != null -> {
                Text(error ?: "Terjadi kesalahan")
            }
        }
    }
}

