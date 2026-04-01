package com.example.sekairatingsystem.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = LightCyan,
    secondary = LightMagenta,
    tertiary = CyberYellow,
    background = LightBackground,
    surface = CardLight,
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

val LocalOshiColor = staticCompositionLocalOf { Color(0xFF33CCBB) }
val LocalOshiOnColor = staticCompositionLocalOf { Color.White }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun SekaiRatingSystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Long? = null,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Turned off dynamic color by default for custom game theme
    content: @Composable () -> Unit,
) {
    val seed = seedColor?.let { Color(it) }

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }

        else -> LightColorScheme
    }

    val oshiColor = seed ?: baseColorScheme.primary
    val onSurfaceText = if (darkTheme) Color(0xFFF5F5F7) else Color(0xFF1C1B1F)
    val onSurfaceVariantText = if (darkTheme) Color(0xFFCED2DA) else Color(0xFF45464F)

    val colorScheme = baseColorScheme.copy(
        background = oshiColor,
        onBackground = onSurfaceText,
        onSurface = onSurfaceText,
        onSurfaceVariant = onSurfaceVariantText,
    )

    val oshiOnColor = oshiColor.contrastText()

    CompositionLocalProvider(
        LocalOshiColor provides oshiColor,
        LocalOshiOnColor provides oshiOnColor,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

private fun Color.contrastText(): Color {
    return if (luminance() > 0.5f) Color(0xFF1B1B1F) else Color(0xFFF5F5F7)
}