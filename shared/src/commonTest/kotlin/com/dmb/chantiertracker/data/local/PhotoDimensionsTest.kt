package com.dmb.chantiertracker.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

fun aJpegHeader(width: Int, height: Int, sofMarker: Int = 0xC0): ByteArray = byteArrayOf(
    0xFF.toByte(), 0xD8.toByte(),
    0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0xFF.toByte(), sofMarker.toByte(), 0x00, 0x11, 0x08,
    (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(),
    0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
    0xFF.toByte(), 0xD9.toByte(),
)

fun aPngHeader(width: Int, height: Int): ByteArray {
    fun int(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
        int(13) + "IHDR".encodeToByteArray() + int(width) + int(height) + byteArrayOf(8, 2, 0, 0, 0)
}

class PhotoDimensionsTest {

    @Test
    fun the_size_of_a_jpeg_is_read_from_its_frame_header() {
        assertEquals(8000 to 6000, pixelSizeOf(aJpegHeader(8000, 6000)))
        assertEquals(4032 to 3024, pixelSizeOf(aJpegHeader(4032, 3024, sofMarker = 0xC2)), "progressive JPEG")
    }

    @Test
    fun the_size_of_a_png_is_read_from_its_header() {
        assertEquals(7000 to 6000, pixelSizeOf(aPngHeader(7000, 6000)))
    }

    @Test
    fun an_unknown_or_truncated_image_has_no_known_size() {
        assertNull(pixelSizeOf("GIF89a".encodeToByteArray()))
        assertNull(pixelSizeOf(aJpegHeader(8000, 6000).copyOf(10)))
        assertNull(pixelSizeOf(ByteArray(0)))
    }

    @Test
    fun a_photo_is_too_large_only_above_forty_megapixels_like_the_server() {
        assertTrue(isAPhotoTooLargeForTheServer(aJpegHeader(8000, 6000)), "48 MP")
        assertTrue(isAPhotoTooLargeForTheServer(aPngHeader(7000, 6000)), "42 MP")
        assertFalse(isAPhotoTooLargeForTheServer(aJpegHeader(8000, 5000)), "exactly 40 MP is accepted")
        assertFalse(isAPhotoTooLargeForTheServer(aJpegHeader(6000, 6000)), "36 MP")
        assertFalse(isAPhotoTooLargeForTheServer("GIF89a".encodeToByteArray()), "unknown size: the server decides")
    }
}
