package cn.hautpocket.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF176B5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2EB), onPrimaryContainer = Color(0xFF13483E),
    secondary = Color(0xFF8B672D), secondaryContainer = Color(0xFFFFEBC2),
    background = Color(0xFFF5F7F8), surface = Color.White,
    surfaceVariant = Color(0xFFEBF0EE), onSurface = Color(0xFF192C2B), onSurfaceVariant = Color(0xFF61716F),
    outlineVariant = Color(0xFFDDE5E2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF95D7BF), onPrimary = Color(0xFF04382C),
    primaryContainer = Color(0xFF164C40), onPrimaryContainer = Color(0xFFB4F0D8),
    background = Color(0xFF111A19), surface = Color(0xFF1A2724), surfaceVariant = Color(0xFF293934),
    secondary = Color(0xFFE6C17F), onSurface = Color(0xFFE0EBE7), onSurfaceVariant = Color(0xFFB6C8C0),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
