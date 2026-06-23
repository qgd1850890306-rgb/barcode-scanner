package com.barcode.scanner.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary, onPrimary = Color.White, secondary = TextSecondary,
    background = Background, surface = Color.White,
    onBackground = TextPrimary, onSurface = TextPrimary,
    error = Danger, onError = Color.White
)

@Composable
fun BarcodeScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, content = content)
}
