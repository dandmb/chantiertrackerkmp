package com.dmb.chantiertracker.data.local

private const val MAX_PHOTO_PIXELS = 40_000_000L
private val JPEG_FRAME_MARKERS = (0xC0..0xCF).toSet() - setOf(0xC4, 0xC8, 0xCC)

fun pixelSizeOf(bytes: ByteArray): Pair<Int, Int>? = jpegSizeOf(bytes) ?: pngSizeOf(bytes)

fun isAPhotoTooLargeForTheServer(bytes: ByteArray): Boolean =
    pixelSizeOf(bytes)?.let { (width, height) -> width.toLong() * height > MAX_PHOTO_PIXELS } ?: false

private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

private fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

private fun jpegSizeOf(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 4 || bytes.u8(0) != 0xFF || bytes.u8(1) != 0xD8) return null
    var at = 2
    while (at + 3 < bytes.size) {
        if (bytes.u8(at) != 0xFF) return null
        val marker = bytes.u8(at + 1)
        when {
            marker == 0xFF -> at += 1
            marker == 0x01 || marker in 0xD0..0xD8 -> at += 2
            marker == 0xD9 || marker == 0xDA -> return null
            marker in JPEG_FRAME_MARKERS -> return if (at + 8 < bytes.size) bytes.u16(at + 7) to bytes.u16(at + 5) else null
            else -> at += 2 + bytes.u16(at + 2)
        }
    }
    return null
}

private fun pngSizeOf(bytes: ByteArray): Pair<Int, Int>? {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(signature)) return null
    if (bytes.copyOfRange(12, 16).decodeToString() != "IHDR") return null
    fun u32(at: Int): Long = (bytes.u16(at).toLong() shl 16) or bytes.u16(at + 2).toLong()
    val width = u32(16)
    val height = u32(20)
    if (width > Int.MAX_VALUE || height > Int.MAX_VALUE) return null
    return width.toInt() to height.toInt()
}
