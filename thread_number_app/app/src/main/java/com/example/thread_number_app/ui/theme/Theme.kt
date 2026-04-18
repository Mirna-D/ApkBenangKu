package com.example.thread_number_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =====================
// COLOR PALETTE FIGMA
// =====================

// Primary purple palette
private val Primary40 = PrimaryPurple
private val Primary80 = PrimaryPurpleLight

// Secondary (pink)
private val Secondary40 = PinkAccent
private val Secondary80 = PinkAccent.copy(alpha = 0.85f)

// Background & surface (FIXED)
private val AppBackgroundLight = BackgroundLight
private val AppSurfaceLight = SurfaceLight   // <-- FIX: gunakan SurfaceLight dari Color.kt

// Text colors
private val TextPrimary = TextDark
private val TextSecondary = TextGray

// =====================
// LIGHT THEME
// =====================
private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Color.White,
    primaryContainer = Primary80,
    onPrimaryContainer = TextDark,

    secondary = Secondary40,
    onSecondary = Color.White,
    secondaryContainer = Secondary80,
    onSecondaryContainer = TextDark,

    background = AppBackgroundLight,
    onBackground = TextPrimary,

    surface = AppSurfaceLight,
    onSurface = TextPrimary,

    tertiary = PinkAccent,
    onTertiary = Color.White
)

// =====================
// DARK THEME
// =====================
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurpleDark,
    secondary = PinkAccent,

    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),

    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

// =====================
// MAIN THEME WRAPPER
// =====================
@Composable
fun Thread_number_appTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}