package com.example.thread_number_app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument

import com.example.thread_number_app.ui.screens.HomeScreenUI
import com.example.thread_number_app.ui.screens.CameraScreen
import com.example.thread_number_app.ui.screens.GalleryPredictionScreen
import com.example.thread_number_app.ui.screens.ResultScreen
import com.example.thread_number_app.ui.viewmodel.ResultViewModel

@Composable
fun AppNavHost(navController: NavHostController) {

    val resultViewModel: ResultViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        navigation(
            startDestination = AppScreen.Home.route,
            route = "main"
        ) {

            // Home
            composable(AppScreen.Home.route) {
                HomeScreenUI(navController)
            }

            // Camera
            composable(AppScreen.Camera.route) {
                CameraScreen(
                    navController = navController,
                    resultViewModel = resultViewModel
                )
            }

            // Gallery
            composable(
                route = "gallery_prediction?uri={uri}",
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->

                val imageUri = entry.arguments?.getString("uri") ?: ""

                GalleryPredictionScreen(
                    navController = navController,
                    imagePath = imageUri,
                    resultViewModel = resultViewModel
                )
            }

            // Result
            composable(AppScreen.Result.route) {
                ResultScreen(
                    navController = navController,
                    resultViewModel = resultViewModel
                )
            }
        }
    }
}
