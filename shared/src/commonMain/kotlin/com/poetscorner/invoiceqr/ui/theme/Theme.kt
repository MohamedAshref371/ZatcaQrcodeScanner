package com.poetscorner.invoiceqr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

// Give each accent its own neutral surfaces, text and supporting colors.
private val GoldenLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF755900), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8AA), onPrimaryContainer = Color(0xFF443200),
    secondary = Color(0xFF6B6045), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E5C8), onSecondaryContainer = Color(0xFF3B321D),
    tertiary = Color(0xFF526447), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E9C7), onTertiaryContainer = Color(0xFF26381D),
    background = Color(0xFFFAF7F0), onBackground = Color(0xFF302B20),
    surface = Color(0xFFFAF7F0), onSurface = Color(0xFF302B20),
    surfaceVariant = Color(0xFFEDE6D8), onSurfaceVariant = Color(0xFF675F50),
    surfaceContainerLowest = Color(0xFFFFFDF8), surfaceContainerLow = Color(0xFFFCF9F3),
    surfaceContainer = Color(0xFFF4EFE5), surfaceContainerHigh = Color(0xFFEDE6D8),
    surfaceContainerHighest = Color(0xFFE5DDCD),
    surfaceDim = Color(0xFFE0D9CB), surfaceBright = Color(0xFFFAF7F0),
    outline = Color(0xFF827763), outlineVariant = Color(0xFFDED5C3),
    inverseSurface = Color(0xFF342F25), inverseOnSurface = Color(0xFFF7F0E3),
    inversePrimary = Color(0xFFE8C46A), surfaceTint = Color(0xFF755900)
)

private val GoldenDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFFE8C46A), onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF594400), onPrimaryContainer = Color(0xFFFFE08F),
    secondary = Color(0xFFD5C6A1), onSecondary = Color(0xFF393019),
    secondaryContainer = Color(0xFF51472F), onSecondaryContainer = Color(0xFFF1E5C8),
    tertiary = Color(0xFFB9CDAA), onTertiary = Color(0xFF25351C),
    tertiaryContainer = Color(0xFF3B4C30), onTertiaryContainer = Color(0xFFD5E9C7),
    background = Color(0xFF1C1912), onBackground = Color(0xFFEDE5D5),
    surface = Color(0xFF1C1912), onSurface = Color(0xFFEDE5D5),
    surfaceVariant = Color(0xFF3B3427), onSurfaceVariant = Color(0xFFCABFA9),
    surfaceContainerLowest = Color(0xFF221E16), surfaceContainerLow = Color(0xFF28231A),
    surfaceContainer = Color(0xFF302A1F), surfaceContainerHigh = Color(0xFF3B3427),
    surfaceContainerHighest = Color(0xFF463E2F),
    surfaceDim = Color(0xFF1C1912), surfaceBright = Color(0xFF423B2F),
    outline = Color(0xFF9C9078), outlineVariant = Color(0xFF4C4332),
    inverseSurface = Color(0xFFEDE5D5), inverseOnSurface = Color(0xFF342F25),
    inversePrimary = Color(0xFF755900), surfaceTint = Color(0xFFE8C46A)
)

private val PurpleLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF6750A4), onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF655C76), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE3F5), onSecondaryContainer = Color(0xFF30283F),
    tertiary = Color(0xFF80546D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EB), onTertiaryContainer = Color(0xFF482039),
    background = Color(0xFFF8F5FC), onBackground = Color(0xFF2C2636),
    surface = Color(0xFFF8F5FC), onSurface = Color(0xFF2C2636),
    surfaceVariant = Color(0xFFEAE3F0), onSurfaceVariant = Color(0xFF635B70),
    surfaceContainerLowest = Color(0xFFFFFBFF), surfaceContainerLow = Color(0xFFFCF9FF),
    surfaceContainer = Color(0xFFF1EBF7), surfaceContainerHigh = Color(0xFFEAE3F0),
    surfaceContainerHighest = Color(0xFFE2D9EB),
    surfaceDim = Color(0xFFDDD5E4), surfaceBright = Color(0xFFF8F5FC),
    outline = Color(0xFF80758D), outlineVariant = Color(0xFFDDD3E7),
    inverseSurface = Color(0xFF322C3C), inverseOnSurface = Color(0xFFF5EDF9),
    inversePrimary = Color(0xFFD0BCFF), surfaceTint = Color(0xFF6750A4)
)

private val PurpleDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCEC1DE), onSecondary = Color(0xFF352C43),
    secondaryContainer = Color(0xFF4C425B), onSecondaryContainer = Color(0xFFECE3F5),
    tertiary = Color(0xFFEAB5D1), onTertiary = Color(0xFF4B263D),
    tertiaryContainer = Color(0xFF653C54), onTertiaryContainer = Color(0xFFFFD8EB),
    background = Color(0xFF1B1623), onBackground = Color(0xFFECE3F3),
    surface = Color(0xFF1B1623), onSurface = Color(0xFFECE3F3),
    surfaceVariant = Color(0xFF393043), onSurfaceVariant = Color(0xFFC8BDD4),
    surfaceContainerLowest = Color(0xFF211B2B), surfaceContainerLow = Color(0xFF282132),
    surfaceContainer = Color(0xFF30273B), surfaceContainerHigh = Color(0xFF393043),
    surfaceContainerHighest = Color(0xFF453A50),
    surfaceDim = Color(0xFF1B1623), surfaceBright = Color(0xFF41374C),
    outline = Color(0xFF9B8DA8), outlineVariant = Color(0xFF493D55),
    inverseSurface = Color(0xFFECE3F3), inverseOnSurface = Color(0xFF322C3C),
    inversePrimary = Color(0xFF6750A4), surfaceTint = Color(0xFFD0BCFF)
)

private val BlueLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF0B57B7), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7FF), onPrimaryContainer = Color(0xFF00326B),
    secondary = Color(0xFF526580), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7F6), onSecondaryContainer = Color(0xFF24374F),
    tertiary = Color(0xFF755784), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1DBF8), onTertiaryContainer = Color(0xFF442452),
    background = Color(0xFFF5F8FD), onBackground = Color(0xFF1B2B42),
    surface = Color(0xFFF5F8FD), onSurface = Color(0xFF1B2B42),
    surfaceVariant = Color(0xFFE5ECF5), onSurfaceVariant = Color(0xFF536275),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBFF),
    surfaceContainer = Color(0xFFEDF2F9), surfaceContainerHigh = Color(0xFFE5ECF5),
    surfaceContainerHighest = Color(0xFFDDE6F1),
    surfaceDim = Color(0xFFD7E0EC), surfaceBright = Color(0xFFF5F8FD),
    outline = Color(0xFF748397), outlineVariant = Color(0xFFD5DFEB),
    inverseSurface = Color(0xFF293950), inverseOnSurface = Color(0xFFF0F4FC),
    inversePrimary = Color(0xFFA9C8FF), surfaceTint = Color(0xFF0B57B7)
)

private val BlueDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFFA9C8FF), onPrimary = Color(0xFF003B80),
    primaryContainer = Color(0xFF174F99), onPrimaryContainer = Color(0xFFD8E7FF),
    secondary = Color(0xFFB9CBE4), onSecondary = Color(0xFF263850),
    secondaryContainer = Color(0xFF3A4C65), onSecondaryContainer = Color(0xFFDCE7F6),
    tertiary = Color(0xFFDCBDE9), onTertiary = Color(0xFF4D305B),
    tertiaryContainer = Color(0xFF654773), onTertiaryContainer = Color(0xFFF1DBF8),
    background = Color(0xFF111C2C), onBackground = Color(0xFFDFE9F6),
    surface = Color(0xFF111C2C), onSurface = Color(0xFFDFE9F6),
    surfaceVariant = Color(0xFF2B3A4F), onSurfaceVariant = Color(0xFFAFBED2),
    surfaceContainerLowest = Color(0xFF172337), surfaceContainerLow = Color(0xFF1C2940),
    surfaceContainer = Color(0xFF223049), surfaceContainerHigh = Color(0xFF2B3A4F),
    surfaceContainerHighest = Color(0xFF35465B),
    surfaceDim = Color(0xFF111C2C), surfaceBright = Color(0xFF3D4E64),
    outline = Color(0xFF8C9DB4), outlineVariant = Color(0xFF3D4F65),
    inverseSurface = Color(0xFFDFE9F6), inverseOnSurface = Color(0xFF293950),
    inversePrimary = Color(0xFF0B57B7), surfaceTint = Color(0xFFA9C8FF)
)

@Composable
fun ZatcaQrcodeScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    goldenTheme: Boolean = false,
    purpleTheme: Boolean = false,
    blueTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = when {
        blueTheme && darkTheme -> BlueDarkColorScheme
        blueTheme -> BlueLightColorScheme
        purpleTheme && darkTheme -> PurpleDarkColorScheme
        purpleTheme -> PurpleLightColorScheme
        goldenTheme && darkTheme -> GoldenDarkColorScheme
        goldenTheme -> GoldenLightColorScheme
        else -> base
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)), content = content)
}
