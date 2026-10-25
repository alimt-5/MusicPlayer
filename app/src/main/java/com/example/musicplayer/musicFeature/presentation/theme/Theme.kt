package com.example.musicplayer.musicFeature.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF2A1800),
    background = Color(0xFF0F0F10),
    surface = Color(0xFF171719),
    surfaceVariant = Color(0xFF29292D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFEF6C00),
    onPrimary = Color.White,
    background = Color(0xFFF7F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9E9ED)
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
