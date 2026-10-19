package com.poetscorner.zatcaqrcodescanner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun ZatcaQrcodeScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    goldenTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        goldenTheme && darkTheme -> darkColorScheme(
            primary = Color(0xFFE8C46A), onPrimary = Color(0xFF3F2E00),
            primaryContainer = Color(0xFF594400), onPrimaryContainer = Color(0xFFFFE08F),
            secondary = Color(0xFFD6C6A1), secondaryContainer = Color(0xFF4F452E),
            background = Color(0xFF17140D), surface = Color(0xFF17140D)
        )
        goldenTheme -> lightColorScheme(
            primary = Color(0xFF755900), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE08F), onPrimaryContainer = Color(0xFF251A00),
            secondary = Color(0xFF685B3D), secondaryContainer = Color(0xFFF0E0BA),
            background = Color(0xFFFFF8ED), surface = Color(0xFFFFF8ED)
        )
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
