package app.mindorbit.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import app.mindorbit.data.OrbitPreferences

private val Light = lightColorScheme(primary = Color(0xFF267C70), onPrimary = Color.White, primaryContainer = Color(0xFFD9F1E9), onPrimaryContainer = Color(0xFF153F34), secondary = Color(0xFF976A20), onSecondary = Color.White, secondaryContainer = Color(0xFFFFE9B8), onSecondaryContainer = Color(0xFF4B3B1E), tertiary = Color(0xFFAF526B), background = Color(0xFFF7F8F7), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEBEFED), surfaceContainer = Color(0xFFEDF1EE), surfaceContainerLow = Color(0xFFF2F5F2), surfaceContainerHigh = Color(0xFFE5EBE6), surfaceContainerHighest = Color(0xFFDCE5DE), surfaceContainerLowest = Color.White, onSurface = Color(0xFF202925), onSurfaceVariant = Color(0xFF59645E))
private val Dark = darkColorScheme(primary = Color(0xFF88D5BF), onPrimary = Color(0xFF00382D), primaryContainer = Color(0xFF214C40), onPrimaryContainer = Color(0xFFD9F1E9), secondary = Color(0xFFE9C578), onSecondary = Color(0xFF3E2E10), secondaryContainer = Color(0xFF534220), onSecondaryContainer = Color(0xFFFFE9B8), tertiary = Color(0xFFFFABC1), background = Color(0xFF121614), surface = Color(0xFF1B211E), surfaceVariant = Color(0xFF2C3630), surfaceContainer = Color(0xFF202824), surfaceContainerLow = Color(0xFF1A211D), surfaceContainerHigh = Color(0xFF29322C), surfaceContainerHighest = Color(0xFF333E36), surfaceContainerLowest = Color(0xFF0D120F), onSurface = Color(0xFFE4ECE6), onSurfaceVariant = Color(0xFFBBC8BF))
@Composable fun OrbitTheme(settings: OrbitPreferences = OrbitPreferences(), content: @Composable () -> Unit) {
    val dark = settings.theme == "Dark" || settings.theme == "System" && isSystemInDarkTheme()
    val colors = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current) } else if (dark) Dark else Light
    val base = Typography()
    fun TextStyle.zero() = copy(letterSpacing = 0.sp)
    MaterialTheme(colorScheme = colors, typography = base.copy(displaySmall = base.displaySmall.zero(), headlineLarge = base.headlineLarge.zero(), headlineMedium = base.headlineMedium.zero(), titleLarge = base.titleLarge.zero(), titleMedium = base.titleMedium.zero(), bodyLarge = base.bodyLarge.zero(), bodyMedium = base.bodyMedium.zero(), labelLarge = base.labelLarge.zero()), content = content)
}
