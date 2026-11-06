package com.ehan.curatcoret.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AppColors.Blue500,
    background = AppColors.Background,
    surface = Color(0xFF0F172A),
    onSurface = Color.White,
    onBackground = Color.White,
)

@Composable
fun CuratCoretTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
