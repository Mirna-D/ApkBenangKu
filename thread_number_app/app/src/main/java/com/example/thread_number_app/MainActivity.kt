package com.example.thread_number_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.thread_number_app.ui.navigation.AppNavHost
import com.example.thread_number_app.ui.theme.Thread_number_appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Thread_number_appTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
