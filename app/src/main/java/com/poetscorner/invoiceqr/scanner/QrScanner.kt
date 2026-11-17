package com.poetscorner.invoiceqr.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/** Software bitmap, bounded memory and EXIF orientation, also on API 26/27. */
fun decodeGallery(context: Context, uri: Uri): Bitmap {
    fun stream() = requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open image" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stream().use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image file" }
    var sample = 1
    while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) sample *= 2
    val bitmap = stream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
        inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
    }) } ?: error("Unable to decode image")
    val exif = stream().use { ExifInterface(it) }
    val matrix = Matrix().apply {
        postRotate(exif.rotationDegrees.toFloat())
        if (exif.isFlipped) postScale(-1f, 1f)
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
}
