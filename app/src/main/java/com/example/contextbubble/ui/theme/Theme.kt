package com.example.contextbubble.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Cool neutral surfaces with a single blue accent (UI_UX.md, Design direction). */
private val BrandBlue = Color(0xFF1E63D6)
private val BrandBlueDark = Color(0xFF8AB4FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FA),
    onPrimaryContainer = Color(0xFF0A2A66),
    background = Color(0xFFF6F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EAF0),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF0A2A66),
    primaryContainer = Color(0xFF1B3D7A),
    onPrimaryContainer = Color(0xFFD9E4FA),
    background = Color(0xFF111418),
    surface = Color(0xFF181C21),
    surfaceVariant = Color(0xFF2A3038),
)

@Composable
fun ContextBubbleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
