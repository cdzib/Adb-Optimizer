package com.optimizer.tools.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optimizer.tools.domain.model.ThemeMode

private val DarkScheme =
    darkColorScheme(
        primary = Color(0xFF4ADE80),
        onPrimary = Color(0xFF052E16),
        secondary = Color(0xFF22D3EE),
        onSecondary = Color(0xFF042F2E),
        tertiary = Color(0xFFFBBF24),
        onTertiary = Color(0xFF2A1B00),
        background = Color(0xFF0B0F14),
        onBackground = Color(0xFFE7EEF7),
        surface = Color(0xFF17212E),
        onSurface = Color(0xFFE7EEF7),
        surfaceVariant = Color(0xFF1A2432),
        onSurfaceVariant = Color(0xFF8BA0B8),
        surfaceContainerLowest = Color(0xFF0B0F14),
        surfaceContainerLow = Color(0xFF101823),
        surfaceContainer = Color(0xFF17212E),
        surfaceContainerHigh = Color(0xFF1A2432),
        surfaceContainerHighest = Color(0xFF22303F),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF1F2B3A),
        error = Color(0xFFF87171),
        onError = Color(0xFF2A0F0F),
    )

private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF15803D),
        onPrimary = Color.White,
        secondary = Color(0xFF0E7490),
        onSecondary = Color.White,
        tertiary = Color(0xFFB45309),
        onTertiary = Color.White,
        background = Color(0xFFF6F8FB),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE9EEF5),
        onSurfaceVariant = Color(0xFF52657A),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF9FBFD),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEFF3F8),
        surfaceContainerHighest = Color(0xFFE4EAF2),
        outline = Color(0xFF7C8BA0),
        outlineVariant = Color(0xFFCBD5E1),
        error = Color(0xFFDC2626),
        onError = Color.White,
    )

private val AppTypography =
    Typography(
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    )

@Composable
fun AdbOptimizerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp),
            ),
        content = content,
    )
}

/** Colores semanticos que siguen el tema activo. */
object AppColors {
    val accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val warning: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val border: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
}
