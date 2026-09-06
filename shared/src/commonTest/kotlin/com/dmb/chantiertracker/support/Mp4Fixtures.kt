package com.dmb.chantiertracker.support

/**
 * Builds a minimal but valid ISO-BMFF byte stream — `ftyp` + `moov` → `mvhd`
 * — carrying a given duration, enough for `probeMp4DurationSeconds` to read.
 * `version` selects the 32-bit (0) or 64-bit (1) `mvhd` layout.
 */
fun mp4Bytes(durationSeconds: Double, timescale: Int = 600, version: Int = 0): ByteArray {
    val durationUnits = (durationSeconds * timescale).toLong()
    val mvhdContent = if (version == 1) {
        buildBytes {
            u8(1); u24(0)          // version + flags
            u64(0); u64(0)         // creation / modification time
            u32(timescale.toLong())
            u64(durationUnits)
            repeat(12) { u8(0) }   // rate, volume, reserved — parser stops before this
        }
    } else {
        buildBytes {
            u8(0); u24(0)          // version + flags
            u32(0); u32(0)         // creation / modification time
            u32(timescale.toLong())
            u32(durationUnits)
            repeat(12) { u8(0) }
        }
    }
    val mvhd = box("mvhd", mvhdContent)
    val moov = box("moov", mvhd)
    val ftyp = box("ftyp", "isom".encodeToByteArray() + byteArrayOf(0, 0, 0, 0) + "isom".encodeToByteArray())
    return ftyp + moov
}

private fun box(type: String, content: ByteArray): ByteArray =
    buildBytes { u32((8 + content.size).toLong()) } + type.encodeToByteArray() + content

private class ByteSink {
    val out = ArrayList<Byte>()
    fun u8(v: Int) { out.add((v and 0xFF).toByte()) }
    fun u24(v: Int) { u8(v ushr 16); u8(v ushr 8); u8(v) }
    fun u32(v: Long) { for (s in intArrayOf(24, 16, 8, 0)) u8((v ushr s).toInt()) }
    fun u64(v: Long) { for (s in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) u8((v ushr s).toInt()) }
}

private fun buildBytes(block: ByteSink.() -> Unit): ByteArray = ByteSink().apply(block).out.toByteArray()
