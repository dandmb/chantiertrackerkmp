package com.dmb.chantiertracker.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

actual val systemPhotoCompressor: PhotoCompressor = PhotoCompressor { photo ->
    withContext(Dispatchers.Default) { runCatching { compressOnAndroid(photo) }.getOrNull() }
}

private fun compressOnAndroid(photo: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(photo, 0, photo.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
    val decoded = BitmapFactory.decodeByteArray(photo, 0, photo.size, options) ?: return null
    val (width, height) = fittedSize(decoded.width, decoded.height)
    val matrix = Matrix().apply {
        postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        applyExifOrientation(orientationOf(photo))
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val jpeg = ByteArrayOutputStream()
    upright.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, jpeg)
    if (upright !== decoded) upright.recycle()
    decoded.recycle()
    return jpeg.toByteArray()
}

private fun orientationOf(photo: ByteArray): Int =
    runCatching { ExifInterface(ByteArrayInputStream(photo)).getAttributeInt(ExifInterface.TAG_ORIENTATION, EXIF_UPRIGHT) }
        .getOrDefault(EXIF_UPRIGHT)

private fun Matrix.applyExifOrientation(orientation: Int) {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
    }
}
