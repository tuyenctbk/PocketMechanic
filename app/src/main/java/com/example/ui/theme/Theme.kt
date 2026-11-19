package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkWorkshopColorScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = SafetyAmberDark,
    primaryContainer = SafetyAmberContainer,
    onPrimaryContainer = SafetyAmberLight,
    secondary = DiagnosticCyan,
    onSecondary = Color(0xFF002536),
    secondaryContainer = DiagnosticCyanContainer,
    onSecondaryContainer = DiagnosticCyanLight,
    tertiary = TorchGold,
    onTertiary = Color(0xFF2B2300),
    background = WorkshopBg,
    onBackground = TextPrimary,
    surface = WorkshopSurface,
    onSurface = TextPrimary,
    surfaceVariant = WorkshopSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = WorkshopSurface,
    surfaceContainer = WorkshopSurfaceElevated,
    surfaceContainerHigh = WorkshopSurfaceBright,
    outline = WorkshopOutline,
    outlineVariant = Color(0xFF243040),
    error = StatusCriticalRed,
    onError = Color.White,
    errorContainer = StatusCriticalContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val LightWorkshopColorScheme = lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = DaylightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),
    background = DaylightBg,
    onBackground = DaylightOnSurface,
    surface = DaylightSurface,
    onSurface = DaylightOnSurface,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightOnSurfaceVariant,
    outline = Color(0xFFCBD5E1),
    error = StatusCriticalRed,
    onError = Color.White
)

private val PocketMechanicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PocketMechanicTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkWorkshopColorScheme else LightWorkshopColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketMechanicTypography,
        shapes = PocketMechanicShapes,
        content = content
    )
}
