package com.dmb.chantiertracker.presentation.logs

import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Reads a video's duration (seconds) straight from the container, with **no
 * platform code and no media library** — it walks the ISO-BMFF box tree
 * (`moov` → `mvhd`) that every phone-camera capture (MP4 / QuickTime `.mov`)
 * uses. Returns `null` for anything it can't parse (exotic container, `moov`
 * truncated, WebM/MKV…) — the caller then just proceeds and lets the server's
 * `ffprobe` be the authority, exactly like the web's `readVideoDurationSeconds`
 * courtesy check.
 *
 * Two entry points:
 * - [probeMp4DurationSeconds] (`ByteArray`) — a small in-memory buffer.
 * - [probeMp4DurationSeconds] (`Source`) — a **stream**: the media payload
 *   (`mdat`, often hundreds of MB) is skipped, never buffered, so a full-size
 *   video is probed without loading it into memory (ADR-38). It reads only up
 *   to [maxScanBytes] from the front — enough to catch every `faststart` file
 *   (iOS captures, anything re-encoded or shared) and any clip whose trailing
 *   `moov` still lands in that budget; past it we give up and let the server's
 *   `ffprobe` decide, same as any other probe miss.
 */
fun probeMp4DurationSeconds(bytes: ByteArray): Double? = runCatching {
    val moov = findTopLevelBox(bytes, "moov") ?: return null
    durationFromMoovContent(bytes, moov.contentStart, moov.end)
}.getOrNull()

// moov's sample tables stay small even for a long clip; anything past this is
// not a `moov` we should try to hold in memory — bail and let the server decide.
private const val MAX_MOOV_BYTES = 32L * 1024 * 1024
private const val DEFAULT_MAX_SCAN_BYTES = 16L * 1024 * 1024

fun probeMp4DurationSeconds(source: Source, maxScanBytes: Long = DEFAULT_MAX_SCAN_BYTES): Double? = runCatching {
    var consumed = 0L
    while (!source.exhausted()) {
        val header = readBoxHeader(source) ?: return null
        consumed += header.headerBytes
        if (header.type == "moov") {
            if (header.contentLength !in 0..MAX_MOOV_BYTES) return null
            val moovContent = source.readByteArray(header.contentLength.toInt())
            return durationFromMoovContent(moovContent, 0, moovContent.size)
        }
        if (header.contentLength <= 0) return null // 0 == "to end of file" for a non-moov box
        if (consumed + header.contentLength > maxScanBytes) return null
        source.skip(header.contentLength)
        consumed += header.contentLength
    }
    null
}.getOrNull()

private data class BoxHeader(val type: String, val contentLength: Long, val headerBytes: Long)

private fun readBoxHeader(source: Source): BoxHeader? {
    if (!source.request(8)) return null
    val size32 = source.readInt().toLong() and 0xFFFFFFFFL
    val type = buildString { repeat(4) { append((source.readByte().toInt() and 0xFF).toChar()) } }
    return when (size32) {
        1L -> {
            if (!source.request(8)) return null
            val large = source.readLong()
            BoxHeader(type, large - 16, headerBytes = 16)
        }
        0L -> BoxHeader(type, -1, headerBytes = 8) // extends to end of file
        else -> BoxHeader(type, size32 - 8, headerBytes = 8)
    }
}

private fun durationFromMoovContent(bytes: ByteArray, from: Int, to: Int): Double? {
    val mvhd = findChildBox(bytes, from, to, "mvhd") ?: return null
    return parseMvhdDuration(bytes, mvhd.contentStart)
}

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
