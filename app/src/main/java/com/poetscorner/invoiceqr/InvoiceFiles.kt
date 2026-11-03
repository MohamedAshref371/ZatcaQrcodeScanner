package com.poetscorner.invoiceqr

import android.content.Intent
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.util.Locale

/** Read both EXTRA_STREAM and ClipData: sharing apps use either or both. */
internal fun Intent.sharedInvoiceUris(): List<Uri> {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
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

/** The system file picker can return a single URI, ClipData, or both. */
internal fun Intent.selectedInvoiceUris(): List<Uri> {
    val clipped = clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
    return (clipped + listOfNotNull(data)).distinct()
}

internal enum class InvoiceFileType { IMAGE, PDF }

/** Resolve each item independently because a mixed share has a general MIME type. */
internal fun Context.invoiceFileType(uri: Uri): InvoiceFileType? {
    val mime = runCatching { contentResolver.getType(uri)?.lowercase(Locale.ROOT) }.getOrNull()
    if (mime == "application/pdf") return InvoiceFileType.PDF
    if (mime?.startsWith("image/") == true) return InvoiceFileType.IMAGE
    val displayName = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    val extension = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    return when (extension) {
        "pdf" -> InvoiceFileType.PDF
        "png", "jpg", "jpeg", "webp", "heic", "heif", "bmp", "gif" -> InvoiceFileType.IMAGE
        else -> null
    }
}
