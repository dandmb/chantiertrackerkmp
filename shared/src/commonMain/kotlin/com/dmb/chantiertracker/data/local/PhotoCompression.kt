package com.dmb.chantiertracker.data.local

const val MAX_PHOTO_SIDE = 2560
const val PHOTO_JPEG_QUALITY = 80

fun interface PhotoCompressor {
    suspend fun compress(photo: ByteArray): ByteArray?
}

expect val systemPhotoCompressor: PhotoCompressor

const val EXIF_UPRIGHT = 1

private const val EXIF_ORIENTATION_TAG = 0x0112
private const val TIFF_SHORT = 3

fun sampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Int {
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= maxSide) sample *= 2
    return sample
}

fun fittedSize(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val ratio = maxSide.toDouble() / longest
    return maxOf(1, kotlin.math.round(width * ratio).toInt()) to maxOf(1, kotlin.math.round(height * ratio).toInt())
}

fun exifOrientationOf(jpeg: ByteArray): Int = runCatching { readExifOrientation(jpeg) }.getOrNull() ?: EXIF_UPRIGHT

private fun readExifOrientation(jpeg: ByteArray): Int? {
    fun u8(at: Int) = jpeg[at].toInt() and 0xFF
    if (jpeg.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return null
    var at = 2
    while (at + 4 <= jpeg.size && u8(at) == 0xFF) {
        val marker = u8(at + 1)
        if (marker == 0xDA || marker == 0xD9) return null
        val length = (u8(at + 2) shl 8) or u8(at + 3)
        if (marker == 0xE1 && jpeg.copyOfRange(at + 4, at + 10).contentEquals("Exif".encodeToByteArray() + byteArrayOf(0, 0))) {
            return orientationInTiff(jpeg, tiff = at + 10)
        }
        at += 2 + length
    }
    return null
}

private fun orientationInTiff(bytes: ByteArray, tiff: Int): Int? {
    val littleEndian = bytes[tiff].toInt() == 'I'.code
    fun u8(at: Int) = bytes[at].toInt() and 0xFF
    fun u16(at: Int) = if (littleEndian) u8(at) or (u8(at + 1) shl 8) else (u8(at) shl 8) or u8(at + 1)
    fun u32(at: Int) = if (littleEndian) u16(at) or (u16(at + 2) shl 16) else (u16(at) shl 16) or u16(at + 2)
    val ifd = tiff + u32(tiff + 4)
    repeat(u16(ifd)) { index ->
        val entry = ifd + 2 + index * 12
        if (u16(entry) == EXIF_ORIENTATION_TAG && u16(entry + 2) == TIFF_SHORT) return u16(entry + 8).takeIf { it in 1..8 }
    }
    return null
}
