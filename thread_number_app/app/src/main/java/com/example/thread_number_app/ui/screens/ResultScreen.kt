package com.example.thread_number_app.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.thread_number_app.ui.navigation.AppScreen
import com.example.thread_number_app.ui.theme.PrimaryPurpleDark
import com.example.thread_number_app.ui.theme.PrimaryPurpleLight
import com.example.thread_number_app.ui.viewmodel.ResultViewModel
import java.io.File

@Composable
fun ResultScreen(
    navController: NavController,
    resultViewModel: ResultViewModel
) {
    val uiState by resultViewModel.uiState.collectAsStateWithLifecycle()
    val top1 = uiState.top1 ?: "-"
    val top2 = uiState.top2 ?: "-"

    // IMAGE MODEL
    val imageModel = uiState.imagePath

    val painter = rememberAsyncImagePainter(model = imageModel)

    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
    )

    LaunchedEffect(painter.state) {
        Log.d("IMAGE_DEBUG", painter.state.toString())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryPurpleLight)
    ) {

        //    WHITE PANEL   
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 80.dp)
                .background(
                    Color.White,
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .padding(top = 160.dp, start = 24.dp, end = 24.dp)
        ) {

            ResultCard("Nomor Benang", top1 ?: "-")
            Spacer(Modifier.height(20.dp))
            ResultCard("Alternatif Nomor Benang", top2 ?: "-")

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    resultViewModel.clear()
                    navController.navigate(AppScreen.Home.route) {
                        popUpTo("main")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Ambil Gambar Lagi")
            }
        }

        //    HEADER   
        Text(
            text = "BenangKu",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        //    IMAGE   
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .height(420.dp)
                .fillMaxWidth(0.78f),
            shape = RoundedCornerShape(28.dp)
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Gambar tidak dapat dimuat")
                    }
                }
                else -> {
                    Image(
                        painter = painter,
                        contentDescription = "Hasil Prediksi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}


@Composable
fun ResultCard(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = PrimaryPurpleDark,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
