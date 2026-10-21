package com.poetscorner.zatcaqrcodescanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00796D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EB), onPrimaryContainer = Color(0xFF004D44),
    secondary = Color(0xFF4C6272), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EAF0), onSecondaryContainer = Color(0xFF233B4A),
    tertiary = Color(0xFF906A2F), tertiaryContainer = Color(0xFFFFEBC8),
    background = Color(0xFFF3F6F8), onBackground = Color(0xFF172E3D),
    surface = Color(0xFFF3F6F8), onSurface = Color(0xFF172E3D),
    surfaceVariant = Color(0xFFE6EDEF), onSurfaceVariant = Color(0xFF526570),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFB),
    surfaceContainer = Color(0xFFEDF2F4), surfaceContainerHigh = Color(0xFFE6EDEF),
    surfaceContainerHighest = Color(0xFFDFE7EA),
    outline = Color(0xFF71838C), outlineVariant = Color(0xFFD9E3E7),
    error = Color(0xFFAF3838), errorContainer = Color(0xFFFFE9E7), onErrorContainer = Color(0xFF762727)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF77D9C5), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF104E44), onPrimaryContainer = Color(0xFFB7F2E4),
    secondary = Color(0xFFB0C7D7), secondaryContainer = Color(0xFF293F4E),
    onSecondaryContainer = Color(0xFFDCEAF3),
    tertiary = Color(0xFFE6C18A), tertiaryContainer = Color(0xFF55401E),
    background = Color(0xFF0F1B24), onBackground = Color(0xFFE0EBF1),
    surface = Color(0xFF0F1B24), onSurface = Color(0xFFE0EBF1),
    surfaceVariant = Color(0xFF263944), onSurfaceVariant = Color(0xFFA8BBC7),
    surfaceContainerLowest = Color(0xFF152530), surfaceContainerLow = Color(0xFF192B36),
    surfaceContainer = Color(0xFF1E303C), surfaceContainerHigh = Color(0xFF263944),
    surfaceContainerHighest = Color(0xFF2E424E),
    outline = Color(0xFF8398A5), outlineVariant = Color(0xFF334955),
    error = Color(0xFFFFB4AD), errorContainer = Color(0xFF542B2C), onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun ZatcaQrcodeScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    goldenTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = when {
        goldenTheme && darkTheme -> base.copy(
            primary = Color(0xFFE8C46A), onPrimary = Color(0xFF3F2E00),
            primaryContainer = Color(0xFF594400), onPrimaryContainer = Color(0xFFFFE08F))
        goldenTheme -> base.copy(
            primary = Color(0xFF755900), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE8AA), onPrimaryContainer = Color(0xFF443200))
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> base
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)), content = content)
}
