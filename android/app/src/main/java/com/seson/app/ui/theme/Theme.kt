package com.seson.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ses10Colors = darkColorScheme(
    primary = Color(0xFFA99BFF),
    onPrimary = Color(0xFF171226),
    secondary = Color(0xFF6FE7D4),
    background = Color(0xFF0D0B14),
    surface = Color(0xFF17141F),
    surfaceVariant = Color(0xFF24202E),
    onBackground = Color(0xFFF3EFFA),
    onSurface = Color(0xFFF3EFFA),
    onSurfaceVariant = Color(0xFFC9C2D4),
)

@Composable
fun Ses10Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Ses10Colors, content = content)
}
