package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.support.mp4Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
}
