package com.egrmeister.lunchpack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Friendly bento-organizer palette. All text colors meet WCAG AA on their backgrounds. */
object LunchColors {
    val Cream = Color(0xFFFFF6E8)
    val CreamDeep = Color(0xFFF7EAD6)
    val Teal = Color(0xFF4A8A85)
    val TealDark = Color(0xFF2B5E5A)
    val TealSoft = Color(0xFFD6EAE7)
    val Peach = Color(0xFFFAD9C1)
    val PaleBlue = Color(0xFFD6E8F4)
    val Navy = Color(0xFF1B2A41)
    val Muted = Color(0xFF4A5568)
    val Outline = Color(0xFF7D7365)
    val Error = Color(0xFF9A3B2E)
}

private val scheme: ColorScheme = lightColorScheme(
    primary = LunchColors.TealDark,
    onPrimary = Color.White,
    primaryContainer = LunchColors.TealSoft,
    onPrimaryContainer = LunchColors.Navy,
    secondary = LunchColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = LunchColors.Peach,
    onSecondaryContainer = LunchColors.Navy,
    tertiary = LunchColors.Navy,
    onTertiary = Color.White,
    tertiaryContainer = LunchColors.PaleBlue,
    onTertiaryContainer = LunchColors.Navy,
    background = LunchColors.Cream,
    onBackground = LunchColors.Navy,
    surface = LunchColors.Cream,
    onSurface = LunchColors.Navy,
    surfaceVariant = LunchColors.CreamDeep,
    onSurfaceVariant = LunchColors.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFAF2),
    surfaceContainer = Color(0xFFFCF1E1),
    surfaceContainerHigh = LunchColors.CreamDeep,
    surfaceContainerHighest = Color(0xFFF1E2CB),
    outline = LunchColors.Outline,
    outlineVariant = Color(0xFFE2D5C1),
    error = LunchColors.Error,
    onError = Color.White,
)

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** User preference from Settings: when true, decorative animations are skipped. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun LunchPackTheme(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
