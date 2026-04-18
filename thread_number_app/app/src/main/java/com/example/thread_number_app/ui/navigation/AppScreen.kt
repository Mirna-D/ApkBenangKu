package com.example.thread_number_app.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class AppScreen(val route: String) {
 
    // HOME
    object Home : AppScreen("home")

 
    // CAMERA
    object Camera : AppScreen("camera")

 
    // GALLERY PREDICTION
    object Gallery : AppScreen("gallery_prediction") {

        fun createRoute(uri: String): String {
            val encodedUri =
                URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())
            return "$route?uri=$encodedUri"
        }
    }

 
    // RESULT SCREEN
    object Result : AppScreen("result_screen")
}
