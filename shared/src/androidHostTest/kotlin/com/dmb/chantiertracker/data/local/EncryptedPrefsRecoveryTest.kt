package com.dmb.chantiertracker.data.local

import java.io.IOException
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedPrefsRecoveryTest {

    @Test
    fun returns_value_without_recovery_when_first_open_succeeds() {
        var wiped = false
        val result = openWithKeystoreRecovery(
            open = { "prefs" },
            onCorruptedState = { wiped = true },
        )
        assertEquals("prefs", result)
        assertFalse(wiped)
    }

    @Test
    fun recovers_once_from_aead_bad_tag_then_reopens() {
        var attempts = 0
        var wiped = false
        val result = openWithKeystoreRecovery(
            open = {
                attempts++
                if (attempts == 1) throw AEADBadTagException("Signature/MAC verification failed")
                "prefs-after-wipe"
            },
            onCorruptedState = { wiped = true },
        )
        assertEquals("prefs-after-wipe", result)
        assertTrue(wiped)
        assertEquals(2, attempts)
    }

    @Test
    fun recovers_once_from_io_failure_on_corrupted_keyset() {
        var attempts = 0
        val result = openWithKeystoreRecovery(
            open = {
                attempts++
                if (attempts == 1) throw IOException("invalid keyset")
                "prefs-after-wipe"
            },
            onCorruptedState = {},
        )
        assertEquals("prefs-after-wipe", result)
        assertEquals(2, attempts)
    }

    @Test
    fun does_not_recover_from_unrelated_runtime_error() {
        var wiped = false
        assertFailsWith<IllegalStateException> {
            openWithKeystoreRecovery(
                open = { throw IllegalStateException("boom") },
                onCorruptedState = { wiped = true },
            )
        }
        assertFalse(wiped)
    }

    @Test
    fun propagates_failure_when_reopen_after_recovery_still_fails() {
        var wiped = false
        assertFailsWith<AEADBadTagException> {
            openWithKeystoreRecovery(
                open = { throw AEADBadTagException("still broken") },
                onCorruptedState = { wiped = true },
            )
        }
        assertTrue(wiped)
    }
}
