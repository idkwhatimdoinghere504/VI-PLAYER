package com.viplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.viplayer.data.ThemeMode

/** Selectable accent colours. Index 0 is the default. */
data class Accent(val name: String, val seed: Color)

val Accents: List<Accent> = listOf(
    Accent("Violet", Color(0xFF7C5CFF)),
    Accent("Aqua", Color(0xFF16B3A3)),
    Accent("Sunset", Color(0xFFFF7043)),
    Accent("Rose", Color(0xFFE0518E)),
    Accent("Lime", Color(0xFF6DA82F)),
    Accent("Azure", Color(0xFF3D8BFD))
)

private val ViTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp)
    )
}

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0B0B10),
    primaryContainer = accent,
    onPrimaryContainer = Color(0xFF0B0B10),
    secondary = accent,
    onSecondary = Color(0xFF0B0B10),
    tertiary = accent,
    background = Color(0xFF0D0D12),
    onBackground = Color(0xFFECECF2),
    surface = Color(0xFF14141B),
    onSurface = Color(0xFFECECF2),
    surfaceVariant = Color(0xFF20202B),
    onSurfaceVariant = Color(0xFFA9A9BA),
    surfaceContainer = Color(0xFF18181F),
    surfaceContainerHigh = Color(0xFF1E1E27),
    surfaceContainerLow = Color(0xFF141419),
    outline = Color(0xFF2F2F3C),
    outlineVariant = Color(0xFF242430),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0A0A)
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF1B1533),
    secondary = accent,
    onSecondary = Color.White,
    tertiary = accent,
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF14131A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14131A),
    surfaceVariant = Color(0xFFEAE8F2),
    onSurfaceVariant = Color(0xFF4C4A57),
    surfaceContainer = Color(0xFFF1F0F7),
    surfaceContainerHigh = Color(0xFFEAE9F1),
    surfaceContainerLow = Color(0xFFF7F6FC),
    outline = Color(0xFFC9C7D4),
    outlineVariant = Color(0xFFDDDBE6),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun ViTheme(
    themeMode: ThemeMode,
    accentIndex: Int,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = Accents[accentIndex.coerceIn(0, Accents.lastIndex)].seed
    val context = LocalContext.current

    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = ViTypography,
        content = content
    )
}
