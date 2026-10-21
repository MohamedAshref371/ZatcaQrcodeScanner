package com.poetscorner.zatcaqrcodescanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System fonts retain native Arabic shaping and respect the user's font size.
private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
    lineHeight = height.sp, letterSpacing = 0.sp)

val Typography = Typography(
    headlineLarge = text(32, 42, FontWeight.Bold),
    headlineMedium = text(28, 38, FontWeight.Bold),
    headlineSmall = text(24, 34, FontWeight.Bold),
    titleLarge = text(21, 30, FontWeight.Bold),
    titleMedium = text(16, 25, FontWeight.SemiBold),
    titleSmall = text(14, 22, FontWeight.SemiBold),
    bodyLarge = text(16, 26), bodyMedium = text(14, 23), bodySmall = text(12, 20),
    labelLarge = text(14, 22, FontWeight.SemiBold),
    labelMedium = text(12, 18, FontWeight.Medium), labelSmall = text(11, 17, FontWeight.Medium)
)
