package com.kardia.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Paleta de colores Premium HSL de 2026
private val IndigoPrimary = Color(0xFF6366F1) // Indigo moderno
private val IndigoDark = Color(0xFF4338CA)
private val TealSecondary = Color(0xFF10B981) // Emerald/Teal vibrante
private val BackgroundDark = Color(0xFF0F172A) // Slate oscuro profundo
private val SurfaceDark = Color(0xFF1E293B) // Slate card
private val TextPrimaryDark = Color(0xFFF8FAFC) // Off-white
private val TextSecondaryDark = Color(0xFF94A3B8) // Muted gray

private val BackgroundLight = Color(0xFFF8FAFC)
private val SurfaceLight = Color(0xFFFFFFFF)
private val TextPrimaryLight = Color(0xFF0F172A)
private val TextSecondaryLight = Color(0xFF475569)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    secondary = TealSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    secondary = TealSecondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
)

@Composable
fun KardiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
