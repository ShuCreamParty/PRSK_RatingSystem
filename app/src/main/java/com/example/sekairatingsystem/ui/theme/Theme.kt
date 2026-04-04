package com.example.sekairatingsystem.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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

private val DarkColorScheme = darkColorScheme(
    primary = LightCyan,
    secondary = LightMagenta,
    tertiary = CyberYellow,
    background = DarkBackground,
    surface = CardDark,
    onPrimary = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
)

val LocalOshiColor = staticCompositionLocalOf { Color(0xFF33CCBB) }
val LocalOshiOnColor = staticCompositionLocalOf { Color.White }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun SekaiRatingSystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Color,
    backgroundColor: Color,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Turned off dynamic color by default for custom game theme
    content: @Composable () -> Unit,
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme

        else -> LightColorScheme
    }

    val onSurfaceText = if (darkTheme) Color(0xFFF5F5F7) else Color(0xFF1C1B1F)
    val onSurfaceVariantText = if (darkTheme) Color(0xFFCED2DA) else Color(0xFF45464F)
    val onBackgroundText = if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White

    val colorScheme = baseColorScheme.copy(
        background = backgroundColor,
        onBackground = onBackgroundText,
        onSurface = onSurfaceText,
        onSurfaceVariant = onSurfaceVariantText,
    )

    val themeOnColor = themeOnColorFor(themeColor)

    CompositionLocalProvider(
        LocalOshiColor provides themeColor,
        LocalOshiOnColor provides themeOnColor,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

private fun themeOnColorFor(themeColor: Color): Color {
    return when (themeColor.toArgb()) {
        UnitLeoNeed.toArgb() -> Color.White
        UnitMoreMoreJump.toArgb() -> Color.White
        UnitNightcord.toArgb() -> Color.White
        UnitVirtualSinger.toArgb() -> Color.Black
        UnitVividBadSquad.toArgb() -> Color.Black
        UnitWonderlandsShowtime.toArgb() -> Color.Black
        else -> if (themeColor.luminance() > 0.5f) Color.Black else Color.White
    }
}
