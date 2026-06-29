package com.leobigott.cercamessenger.core.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CercaColors {
    val Background = Color(0xFF0B1120)
    val Surface = Color(0xFF111827)
    val SurfaceAlt = Color(0xFF1F2937)
    val Primary = Color(0xFF38BDF8)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFFACC15)
    val Danger = Color(0xFFEF4444)
    val Text = Color(0xFFF9FAFB)
    val Muted = Color(0xFF9CA3AF)
}

private val DarkScheme = darkColorScheme(
    primary = CercaColors.Primary,
    background = CercaColors.Background,
    surface = CercaColors.Surface,
    onPrimary = Color(0xFF031826),
    onBackground = CercaColors.Text,
    onSurface = CercaColors.Text,
    secondary = CercaColors.Success,
    tertiary = CercaColors.Warning,
    error = CercaColors.Danger
)

@Composable
fun CercaMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
