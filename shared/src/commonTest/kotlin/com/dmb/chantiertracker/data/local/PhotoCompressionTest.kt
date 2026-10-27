package com.dmb.chantiertracker.data.local

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCompressionTest {

    @Test
    fun the_decoder_skips_pixels_by_powers_of_two_while_the_longest_side_stays_at_least_2560() {
        assertEquals(1, sampleSizeFor(4000, 3000), "4000/2 = 2000 < 2560: full decode")
        assertEquals(2, sampleSizeFor(8000, 6000))
        assertEquals(4, sampleSizeFor(3000, 12_000), "portrait: the longest side counts")
        assertEquals(1, sampleSizeFor(800, 600))
    }

    @Test
    fun the_longest_side_is_brought_to_2560_keeping_the_proportions() {
        assertEquals(2560 to 1707, fittedSize(6000, 4000))
        assertEquals(1920 to 2560, fittedSize(3000, 4000))
        assertEquals(2560 to 2560, fittedSize(9000, 9000))
        assertEquals(800 to 600, fittedSize(800, 600), "a small photo is never enlarged")
        assertEquals(2560 to 1440, fittedSize(2560, 1440))
    }

    @Test
    fun the_exif_orientation_is_read_in_both_byte_orders() {
        assertEquals(6, exifOrientationOf(jpegWithExif(orientation = 6, littleEndian = true)))
        assertEquals(8, exifOrientationOf(jpegWithExif(orientation = 8, littleEndian = false)))
        assertEquals(3, exifOrientationOf(jpegWithExif(orientation = 3, littleEndian = false, withGps = true)))
    }

    @Test
    fun a_photo_without_exif_or_not_a_jpeg_is_upright() {
        assertEquals(1, exifOrientationOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())))
        assertEquals(1, exifOrientationOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertEquals(1, exifOrientationOf(ByteArray(0)))
    }
}

fun jpegWithExif(orientation: Int, littleEndian: Boolean = true, withGps: Boolean = false, body: ByteArray = byteArrayOf(0xFF.toByte(), 0xD9.toByte())): ByteArray {
    fun u16(v: Int) = if (littleEndian) byteArrayOf(v.toByte(), (v shr 8).toByte()) else byteArrayOf((v shr 8).toByte(), v.toByte())
    fun u32(v: Int) = if (littleEndian) byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    else byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    val entries = if (withGps) 2 else 1
    var tiff = (if (littleEndian) "II".encodeToByteArray() else "MM".encodeToByteArray()) + u16(42) + u32(8) + u16(entries)
    tiff += u16(0x0112) + u16(3) + u32(1) + u16(orientation) + u16(0)
    if (withGps) {
        val gpsOffset = 8 + 2 + entries * 12 + 4
        tiff += u16(0x8825) + u16(4) + u32(1) + u32(gpsOffset)
    }
    tiff += u32(0)
    if (withGps) tiff += u16(1) + u16(0x0001) + u16(2) + u32(2) + "N".encodeToByteArray() + byteArrayOf(0, 0, 0) + u32(0)
    val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
    val length = payload.size + 2
    return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload + body
}
