package com.poetscorner.invoiceqr

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Read both EXTRA_STREAM and ClipData: sharing apps use either or both. */
internal fun Intent.sharedImageUris(): List<Uri> {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
    if (type?.startsWith("image/") != true) return emptyList()
    val clipped = clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
    val streams = if (action == Intent.ACTION_SEND) {
        listOfNotNull(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else {
            @Suppress("DEPRECATION")
            getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        })
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }
    return (clipped + streams).filter { it.scheme == "content" || it.scheme == "file" }.distinct()
}
