package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.support.mp4Bytes
import com.dmb.chantiertracker.support.mp4BytesWithMdat
import kotlinx.io.Buffer
import kotlinx.io.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun probeStream(bytes: ByteArray): Double? =
    probeMp4DurationSeconds(Buffer().apply { write(bytes) })

class Mp4DurationTest {

    @Test
    fun reads_the_duration_from_a_version_0_mvhd() {
        val seconds = probeMp4DurationSeconds(mp4Bytes(durationSeconds = 42.0))
        assertNotNull(seconds)
        assertTrue(seconds in 41.9..42.1, "got $seconds")
    }

    @Test
    fun reads_the_duration_from_a_version_1_mvhd() {
        val seconds = probeMp4DurationSeconds(mp4Bytes(durationSeconds = 300.0, timescale = 30_000, version = 1))
        assertNotNull(seconds)
        assertTrue(seconds in 299.9..300.1, "got $seconds")
    }

    @Test
    fun fractional_durations_survive() {
        val seconds = probeMp4DurationSeconds(mp4Bytes(durationSeconds = 119.4))
        assertNotNull(seconds)
        assertTrue(seconds in 119.3..119.5, "got $seconds")
    }

    @Test
    fun returns_null_for_bytes_that_are_not_an_mp4() {
        assertNull(probeMp4DurationSeconds(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        assertNull(probeMp4DurationSeconds(ByteArray(0)))
        assertNull(probeMp4DurationSeconds("just some text, definitely not a movie".encodeToByteArray()))
    }

    @Test
    fun returns_null_when_timescale_is_zero() {
        assertNull(probeMp4DurationSeconds(mp4Bytes(durationSeconds = 10.0, timescale = 0)))
    }

    // ─── streaming probe (ADR-38) — never buffers the mdat payload ────────────

    @Test
    fun streaming_probe_reads_the_duration_with_moov_before_a_big_mdat() {
        val seconds = probeStream(mp4BytesWithMdat(durationSeconds = 90.0, mdatSize = 5_000_000, moovAtEnd = false))
        assertNotNull(seconds)
        assertTrue(seconds in 89.9..90.1, "got $seconds")
    }

    @Test
    fun streaming_probe_streams_past_a_moderate_mdat_to_reach_a_trailing_moov() {
        val seconds = probeStream(mp4BytesWithMdat(durationSeconds = 110.0, mdatSize = 8_000_000, moovAtEnd = true))
        assertNotNull(seconds)
        assertTrue(seconds in 109.9..110.1, "got $seconds")
    }

    @Test
    fun streaming_probe_gives_up_on_a_trailing_moov_past_the_scan_budget() {
        // A big non-faststart clip: mdat larger than the scan budget → null,
        // the server's ffprobe is the authority (ADR-38).
        assertNull(probeStream(mp4BytesWithMdat(durationSeconds = 110.0, mdatSize = 40_000_000, moovAtEnd = true)))
    }

    @Test
    fun streaming_probe_returns_null_for_a_non_mp4_stream() {
        assertNull(probeStream("definitely not a movie, just words".encodeToByteArray()))
        assertNull(probeStream(ByteArray(0)))
    }
}
