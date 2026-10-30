package cn.hautpocket.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hautpocket.app.domain.TextColors

private val Light = lightColorScheme(
    primary = Color(0xFF176B5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2EB), onPrimaryContainer = Color(0xFF13483E),
    secondary = Color(0xFF8B672D), secondaryContainer = Color(0xFFFFEBC2),
    background = Color(0xFFF3F5F7), surface = Color.White,
    surfaceVariant = Color(0xFFEAEEF2), onSurface = Color(0xFF192C2B), onSurfaceVariant = Color(0xFF61716F),
    outlineVariant = Color(0xFFDDE2E8),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF95D7BF), onPrimary = Color(0xFF04382C),
    primaryContainer = Color(0xFF164C40), onPrimaryContainer = Color(0xFFB4F0D8),
    background = Color(0xFF101215), surface = Color(0xFF1C2025), surfaceVariant = Color(0xFF292F36),
    secondary = Color(0xFFE6C17F), onSurface = Color(0xFFE0EBE7), onSurfaceVariant = Color(0xFFB6C8C0),
)

internal val LocalReduceMotion = staticCompositionLocalOf { false }
private val PocketShapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp))
private val PocketTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 38.sp, letterSpacing = (-.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun PocketTheme(themeColor: String = "green", textColor: String = "auto", reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) Dark else Light
    val primary = when (themeColor) {
        "blue" -> if (dark) Color(0xFF9CCBFF) else Color(0xFF285F99)
        "purple" -> if (dark) Color(0xFFD4BBFF) else Color(0xFF6B5198)
        "orange" -> if (dark) Color(0xFFFFBE8B) else Color(0xFF975022)
        else -> if (TextColors.validCustom(themeColor)) Color(android.graphics.Color.parseColor(themeColor.trim())) else base.primary
    }
    val container = when (themeColor) {
        "blue" -> if (dark) Color(0xFF1B4268) else Color(0xFFD8E9FF)
        "purple" -> if (dark) Color(0xFF4D3970) else Color(0xFFEDE0FF)
        "orange" -> if (dark) Color(0xFF71391B) else Color(0xFFFFE2CD)
        else -> if (TextColors.validCustom(themeColor)) lerp(base.surface, primary, if (dark) .28f else .12f) else base.primaryContainer
    }
    val text = TextColors.palette(textColor, dark)
    val foreground = Color(android.graphics.Color.parseColor(text.first))
    val secondaryText = Color(android.graphics.Color.parseColor(text.second))
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) { MaterialTheme(colorScheme = base.copy(primary = primary, onPrimary = if (primary.luminance() > .179f) Color.Black else Color.White, primaryContainer = container, secondaryContainer = container,
        onSurface = foreground, onBackground = foreground, onSurfaceVariant = secondaryText,
        onPrimaryContainer = foreground, onSecondaryContainer = foreground), shapes = PocketShapes, typography = PocketTypography, content = content) }
}
