package com.dmb.chantiertracker.presentation.logs

/**
 * Reads a video's duration (seconds) straight from the bytes, with **no
 * platform code and no media library** — it walks the ISO-BMFF box tree
 * (`moov` → `mvhd`) that every phone-camera capture (MP4 / QuickTime `.mov`)
 * uses. Returns `null` for anything it can't parse (exotic container, `moov`
 * at the end and truncated read, WebM/MKV…) — the caller then just proceeds
 * and lets the server's `ffprobe` be the authority, exactly like the web's
 * `readVideoDurationSeconds` courtesy check.
 *
 * Only the top-level boxes and `moov`'s direct children are scanned — cheap,
 * and `mvhd` is always the first child of `moov`.
 */
fun probeMp4DurationSeconds(bytes: ByteArray): Double? = runCatching {
    val moov = findTopLevelBox(bytes, "moov") ?: return null
    val mvhd = findChildBox(bytes, moov.contentStart, moov.end, "mvhd") ?: return null
    parseMvhdDuration(bytes, mvhd.contentStart)
}.getOrNull()

private data class Box(val contentStart: Int, val end: Int)

private fun findTopLevelBox(bytes: ByteArray, type: String): Box? =
    findChildBox(bytes, 0, bytes.size, type)

private fun findChildBox(bytes: ByteArray, from: Int, to: Int, type: String): Box? {
    var offset = from
    while (offset + 8 <= to) {
        val size32 = readU32(bytes, offset)
        val boxType = readType(bytes, offset + 4)
        val (contentStart, boxSize) = when (size32) {
            1L -> {
                if (offset + 16 > to) return null
                (offset + 16) to readU64(bytes, offset + 8)
            }
            0L -> (offset + 8) to (to - offset).toLong() // extends to the end of the parent
            else -> (offset + 8) to size32
        }
        val end = offset + boxSize.toInt()
        if (boxSize < 8 || end > to || end <= offset) return null
        if (boxType == type) return Box(contentStart, end)
        offset = end
    }
    return null
}

/** `mvhd`: version(1) flags(3) [creation/modification times] timescale(4) duration. */
private fun parseMvhdDuration(bytes: ByteArray, contentStart: Int): Double? {
    val version = bytes[contentStart].toInt() and 0xFF
    return if (version == 1) {
        val timescale = readU32(bytes, contentStart + 20)
        val duration = readU64(bytes, contentStart + 24)
        if (timescale == 0L) null else duration.toDouble() / timescale
    } else {
        val timescale = readU32(bytes, contentStart + 12)
        val duration = readU32(bytes, contentStart + 16)
        if (timescale == 0L) null else duration.toDouble() / timescale
    }
}

private fun readU32(b: ByteArray, i: Int): Long {
    var v = 0L
    for (k in 0 until 4) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
    return v
}

private fun readU64(b: ByteArray, i: Int): Long {
    var v = 0L
    for (k in 0 until 8) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
    return v
}

private fun readType(b: ByteArray, i: Int): String =
    buildString { for (k in 0 until 4) append((b[i + k].toInt() and 0xFF).toChar()) }
