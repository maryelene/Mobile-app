package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    background = PaperCream,
    onBackground = InkSlate,
    surface = PaperSurface,
    onSurface = InkSlate,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = PaperOutline
)

private val DarkColors = darkColorScheme(
    primary = NightCobalt,
    onPrimary = Color(0xFF0A1D5C),
    primaryContainer = NightCobaltContainer,
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = NightSage,
    onSecondary = Color(0xFF052E20),
    secondaryContainer = NightSageContainer,
    onSecondaryContainer = Color(0xFFCFF3E3),
    tertiary = NightAmber,
    onTertiary = Color(0xFF3E2400),
    tertiaryContainer = NightAmberContainer,
    onTertiaryContainer = Color(0xFFFFEBB8),
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF560A18),
    errorContainer = Color(0xFF7D1B2C),
    background = NightInk,
    onBackground = NightText,
    surface = NightCard,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMuted,
    outline = NightOutline
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EduFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
