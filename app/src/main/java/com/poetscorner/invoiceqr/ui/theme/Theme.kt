package com.poetscorner.invoiceqr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.poetscorner.invoiceqr.sharedui.InvoiceAppTheme
import com.poetscorner.invoiceqr.sharedui.standardInvoiceColors

@Composable
fun ZatcaQrcodeScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    goldenTheme: Boolean = false,
    purpleTheme: Boolean = false,
    blueTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (!goldenTheme && !purpleTheme && !blueTheme &&
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        standardInvoiceColors(darkTheme, goldenTheme, purpleTheme, blueTheme)
    }
    InvoiceAppTheme(colorScheme, content)
}
