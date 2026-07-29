package com.blockforge.editor.ui.theme

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

/** Editor surface colours. Deliberately dark so the saturated block colours carry the hierarchy. */
object ForgeColors {
    val Canvas = Color(0xFF0D1017)
    val CanvasGrid = Color(0xFF181D28)
    val Panel = Color(0xFF141926)
    val PanelRaised = Color(0xFF1B2231)
    val Outline = Color(0xFF2A3244)
    val TextPrimary = Color(0xFFE8EDF7)
    val TextMuted = Color(0xFF8E9AB3)
    val Accent = Color(0xFF4FC3F7)
    val Danger = Color(0xFFEF6F6C)
    val Success = Color(0xFF3FB950)
    val Running = Color(0xFFFFE08A)
}

private val DarkScheme = darkColorScheme(
    primary = ForgeColors.Accent,
    onPrimary = Color(0xFF04121A),
    secondary = Color(0xFFA66BFF),
    background = ForgeColors.Canvas,
    onBackground = ForgeColors.TextPrimary,
    surface = ForgeColors.Panel,
    onSurface = ForgeColors.TextPrimary,
    surfaceVariant = ForgeColors.PanelRaised,
    onSurfaceVariant = ForgeColors.TextMuted,
    outline = ForgeColors.Outline,
    error = ForgeColors.Danger
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B72A8),
    background = Color(0xFFF3F5FA),
    surface = Color(0xFFFFFFFF)
)

private val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ForgeTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun BlockForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        // The editor is a dark tool by design; light mode only softens the chrome.
        colorScheme = if (darkTheme) DarkScheme else DarkScheme.copy(background = LightScheme.background),
        typography = ForgeTypography,
        shapes = ForgeShapes,
        content = content
    )
}
