package app.lumira.wallpapers.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Signature Lumira palette (violet -> mint) used when dynamic color is off. */
val LumiraViolet = Color(0xFF8B7BFF)
val LumiraMint = Color(0xFF6FE7C4)
val LumiraInk = Color(0xFF0B0B10)
val LumiraTrueBlack = Color(0xFF000000)

private val DarkColors = darkColorScheme(
    primary = LumiraViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2550),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = LumiraMint,
    onSecondary = Color(0xFF06281F),
    tertiary = Color(0xFFFFB4C8),
    background = Color(0xFF101016),
    onBackground = Color(0xFFE7E3EC),
    surface = Color(0xFF14141B),
    onSurface = Color(0xFFE7E3EC),
    surfaceVariant = Color(0xFF1E1E28),
    onSurfaceVariant = Color(0xFFC9C4D4),
    outline = Color(0xFF6D6878),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A46E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF),
    onPrimaryContainer = Color(0xFF190066),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFE7E0F0),
    onSurfaceVariant = Color(0xFF494551),
)

private val AmoledColors = DarkColors.copy(
    background = LumiraTrueBlack,
    surface = LumiraTrueBlack,
    surfaceVariant = Color(0xFF0A0A0D),
    onSurfaceVariant = Color(0xFFB9B3C6),
)

/** Extra tokens not covered by MaterialTheme.colorScheme. */
data class LumiraExtraColors(
    val amoled: Boolean,
    val scrim: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
)

val LocalLumiraExtras = staticCompositionLocalOf {
    LumiraExtraColors(
        amoled = false,
        scrim = Color(0xCC000000),
        shimmerBase = Color(0xFF1A1A22),
        shimmerHighlight = Color(0xFF2A2A36),
    )
}

/**
 * @param darkTheme follow system dark mode
 * @param dynamicColor Material You (Android 12+), user-toggleable
 * @param amoled pure-black surfaces (OLED power saving)
 */
@Composable
fun LumiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        amoled && darkTheme -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context).copy(
                background = LumiraTrueBlack,
                surface = LumiraTrueBlack,
            )
        } else {
            AmoledColors
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val effectiveDark = colorScheme.background.luminanceIsLow()
    val extras = LumiraExtraColors(
        amoled = amoled && darkTheme,
        scrim = Color(0xCC000000),
        shimmerBase = if (effectiveDark) Color(0xFF1A1A22) else Color(0xFFE9E6F0),
        shimmerHighlight = if (effectiveDark) Color(0xFF2A2A36) else Color(0xFFF6F4FA),
    )

    CompositionLocalProvider(LocalLumiraExtras provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumiraTypography,
            shapes = LumiraShapes,
            content = content,
        )
    }
}

private fun Color.luminanceIsLow(): Boolean {
    val r = red
    val g = green
    val b = blue
    return (0.299f * r + 0.587f * g + 0.114f * b) < 0.5f
}
