package com.example.thread_number_app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.thread_number_app.R
import com.example.thread_number_app.ui.navigation.AppScreen
import com.example.thread_number_app.ui.theme.PrimaryPurple
import com.example.thread_number_app.ui.theme.PrimaryPurpleLight

@Composable
fun HomeScreenUI(navController: NavController) {

    val context = LocalContext.current
    var selectedImage: Uri? by remember { mutableStateOf(null) }

    // === PILIH GAMBAR DARI GALERI ===
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImage = uri

            // Encode URI agar aman
            val encodedUri = Uri.encode(uri.toString())

            navController.navigate(
                "${AppScreen.Gallery.route}?uri=$encodedUri"
            )

        } else {
            Toast.makeText(context, "Tidak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(150.dp))

        Image(
            painter = painterResource(id = R.drawable.needle),
            contentDescription = null,
            modifier = Modifier.size(230.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "BenangKu",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Identifikasi Nomor Benang",
            fontSize = 14.sp,
            color = Color(0xFF8E8E8E),
            textAlign = TextAlign.Center
        )

        Text(
            text = "BenangMu, Lebih Pasti dengan BenangKu",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryPurpleLight,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(155.dp))

        // ======================================================
        // BUTTON PILIH GAMBAR
        // ======================================================
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .background(
                        color = PrimaryPurpleLight,
                        shape = RoundedCornerShape(30.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pilih Gambar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ======================================================
        // BUTTON AMBIL GAMBAR (CAMERA)
        // ======================================================
        Button(
            onClick = {
                navController.navigate(AppScreen.Camera.route)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .background(
                        color = PrimaryPurple,
                        shape = RoundedCornerShape(30.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ambil Gambar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreenUI(navController = rememberNavController())
}
