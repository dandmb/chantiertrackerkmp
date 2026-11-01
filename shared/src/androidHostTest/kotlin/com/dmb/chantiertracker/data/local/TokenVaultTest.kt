package com.dmb.chantiertracker.data.local

import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenVaultTest {

    private val tokens = AuthTokens("access-secret", "refresh-secret")
    private val directory = createTempDirectory("tokens").toFile()
    private var names = 0
    private fun file() = File(directory, "t${names++}.enc")

    private class ReversingAead : Aead {
        var opens = true
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?) = plaintext.reversedArray()
        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
            if (!opens) throw GeneralSecurityException("wrong key")
            return ciphertext.reversedArray()
        }
    }

    private class FakeLegacy(var stored: AuthTokens?, val readFails: Boolean = false) : LegacyTokens {
        var present = true
        var reads = 0
        override fun exist() = present
        override fun read(): AuthTokens? {
            reads++
            if (readFails) throw IOException("keystore lost")
            return stored
        }
        override fun erase() {
            present = false
            stored = null
        }
    }

    private fun storage(file: File, legacy: LegacyTokens, aead: Aead = ReversingAead()) =
        AndroidTokenStorage(openTokenVault(file, legacy) { aead })

    private fun noLegacy() = FakeLegacy(null).apply { present = false }

    @Test
    fun tokens_come_back_and_the_file_holds_only_what_the_cipher_wrote() = runTest {
        val file = file()
        val storage = storage(file, noLegacy())

        storage.save(tokens)

        assertEquals(tokens, storage.get())
        assertFalse("access-secret" in file.readBytes().decodeToString())
    }

    @Test
    fun clearing_leaves_nothing() = runTest {
        val file = file()
        val storage = storage(file, noLegacy())
        storage.save(tokens)

        storage.clear()

        assertNull(storage.get())
        assertEquals(0, file.length())
    }

    @Test
    fun the_tokens_of_an_older_version_move_once_and_the_old_store_is_erased() = runTest {
        val legacy = FakeLegacy(tokens)
        val storage = storage(file(), legacy)

        assertEquals(tokens, storage.get())
        assertEquals(tokens, storage.get())

        assertEquals(1, legacy.reads)
        assertFalse(legacy.exist())
    }

    @Test
    fun an_unreadable_old_store_means_signing_in_again_and_is_erased() = runTest {
        val legacy = FakeLegacy(tokens, readFails = true)
        val storage = storage(file(), legacy)

        assertNull(storage.get())
        assertFalse(legacy.exist())
    }

    @Test
    fun a_new_session_is_never_replaced_by_old_tokens() = runTest {
        val file = file()
        val aead = ReversingAead()
        val fresh = AuthTokens("fresh-access", "fresh-refresh")
        val first = openTokenVault(file, noLegacy()) { aead }
        first.updateData { TokenVault(fresh) }
        assertEquals(fresh, first.data.first().tokens)

        val migration = LegacyTokensMigration(FakeLegacy(tokens))

        assertEquals(fresh, migration.migrate(TokenVault(fresh)).tokens)
    }

    @Test
    fun a_file_that_cannot_be_opened_means_signing_in_again() = runTest {
        val file = file()
        val aead = ReversingAead()
        storage(file, noLegacy(), aead).save(tokens)
        aead.opens = false

        val reopened = storage(File(file.path), noLegacy(), aead)

        assertTrue(runCatching { reopened.get() }.isSuccess)
    }
}
