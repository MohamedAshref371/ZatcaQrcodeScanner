package com.poetscorner.invoiceqr.sharedui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun invoiceScreenController(): UIViewController = ComposeUIViewController { InvoiceCheckScreen() }
