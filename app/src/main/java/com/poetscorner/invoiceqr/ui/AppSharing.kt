package com.poetscorner.invoiceqr.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.poetscorner.invoiceqr.R

internal fun shareApp(context: Context): Boolean = try {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_name) + "\n" +
            "https://play.google.com/store/apps/details?id=com.poetscorner.invoiceqr")
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_app))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
    true
} catch (_: ActivityNotFoundException) {
    false
}
