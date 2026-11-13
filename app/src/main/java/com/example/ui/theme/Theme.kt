package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NickyCutDarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = Color(0xFF032114),
    primaryContainer = EmeraldDim,
    onPrimaryContainer = Color(0xFFB4FDE0),
    secondary = CyanAccent,
    onSecondary = Color(0xFF022027),
    secondaryContainer = Color(0xFF093640),
    onSecondaryContainer = Color(0xFFBDF5FF),
    tertiary = PurpleAccent,
    onTertiary = Color(0xFF23053B),
    tertiaryContainer = Color(0xFF331152),
    onTertiaryContainer = Color(0xFFEED8FF),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = EmeraldDeep,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceHighlight,
    outline = BorderSubtle,
    outlineVariant = Color(0xFF152D22),
    error = RoseError,
    onError = Color.White,
    errorContainer = Color(0xFF4C0D1B),
    onErrorContainer = Color(0xFFFFD8DF)
)

val NickyCutShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val StudioBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        ObsidianBg,
        Color(0xFF07150F),
        Color(0xFF040A07)
    )
)

val HeroEmeraldGlowBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0D2B1F),
        Color(0xFF071A13),
        Color(0xFF0B1F29)
    )
)

@Composable
fun NickyCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NickyCutDarkColorScheme,
        typography = NickyCutTypography,
        shapes = NickyCutShapes,
        content = content
    )
}
