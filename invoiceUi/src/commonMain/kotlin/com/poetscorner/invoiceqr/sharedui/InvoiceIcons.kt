package com.poetscorner.invoiceqr.sharedui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Same paths as the Android application's 24 dp drawable icons.
internal val ScanIcon = invoiceIcon("Scan",
    "M8,3 L3,3 L3,8 M16,3 L21,3 L21,8 M21,16 L21,21 L16,21 M8,21 L3,21 L3,16 M7,7 L10,7 L10,10 L7,10 Z M14,7 L17,7 L17,10 L14,10 Z M7,14 L10,14 L10,17 L7,17 Z M14,14 L17,14 L17,17 M14,17 L14,15")

internal val ReceiptIcon = invoiceIcon("Receipt",
    "M6,3 L18,3 L18,21 L15,19 L12,21 L9,19 L6,21 Z M9,7 L15,7 M9,11 L15,11 M9,15 L12,15")

internal val HistoryIcon = invoiceIcon("History",
    "M3,10 A9,9 0,1 1,5,18 M3,4 L3,10 L9,10 M12,7 L12,12 L15,14")

internal val StatisticsIcon = invoiceIcon("Statistics",
    "M4,3 L4,21 L22,21 M8,17 L8,12 M13,17 L13,6 M18,17 L18,9")

internal val SettingsIcon = invoiceIcon("Settings",
    "M9,3 L15,3 L16,6 L19,7 L21,12 L19,17 L16,18 L15,21 L9,21 L8,18 L5,17 L3,12 L5,7 L8,6 Z M16,12 A4,4 0,1 1,8,12 A4,4 0,1 1,16,12")

internal val CheckIcon = invoiceIcon("Check",
    "M5,12 L10,17 L20,7")

internal val InfoIcon = invoiceIcon("Info",
    "M21,12 A9,9 0,1 1,3,12 A9,9 0,1 1,21,12 M12,11 L12,16 M12,7 L12.01,7")

internal val CopyIcon = invoiceIcon("Copy",
    "M8,8 L20,8 L20,21 L8,21 Z M16,4 L4,4 L4,17")

private fun invoiceIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
