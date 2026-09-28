package com.dmb.chantiertracker.data.local

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTokenStorageTest {

    private val dir = Files.createTempDirectory("chantiertracker-tokens")
    private val storage = DesktopTokenStorage(dir)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun save_then_get_round_trips() = runTest {
        val tokens = AuthTokens("access-abc.def.ghi", "refresh-uuid-123")
        storage.save(tokens)
        assertEquals(tokens, storage.get())
    }

    @Test
    fun clear_removes_persisted_tokens() = runTest {
        storage.save(AuthTokens("a", "r"))
        storage.clear()
        assertNull(storage.get())
    }

    @Test
    fun persisted_file_is_not_plaintext() = runTest {
        storage.save(AuthTokens("super-secret-access", "super-secret-refresh"))
        val bytes = dir.resolve("auth.bin").readBytes()
        val asText = bytes.decodeToString()
        assertFalse(asText.contains("super-secret-access"))
        assertFalse(asText.contains("super-secret-refresh"))
    }

    @Test
    fun key_and_data_files_are_owner_only() = runTest {
        storage.save(AuthTokens("a", "r"))
        for (name in listOf("auth.key", "auth.bin")) {
            val perms = Files.getPosixFilePermissions(dir.resolve(name))
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
        }
    }

    @Test
    fun tampered_ciphertext_yields_null_rather_than_crashing() = runTest {
        storage.save(AuthTokens("a", "r"))
        val file = dir.resolve("auth.bin")
        val corrupted = file.readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        Files.write(file, corrupted)
        assertNull(storage.get())
    }
}
