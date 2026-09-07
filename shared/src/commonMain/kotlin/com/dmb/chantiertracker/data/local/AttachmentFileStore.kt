package com.dmb.chantiertracker.data.local

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write

// Attachments are the first entity in this app whose payload is a binary
// blob, not JSON — Room stores only a reference, never the bytes (see
// AttachmentEntity). Kept as an interface, like TokenStorage/OnboardingStore,
// so repository tests can substitute an in-memory fake instead of touching
// the real filesystem.
//
// What Room stores is a **stable key** (just the file name), NOT an absolute
// path (ADR-41): on iOS the app's Data-container path changes across
// installs/updates, so a stored absolute path goes stale and the file (video
// or image) becomes unreachable. The key is resolved against
// `FileKit.filesDir` — always current — at the point of use.
interface AttachmentFileStore {
    /** Writes the bytes and returns the stable key to store in Room. */
    suspend fun save(bytes: ByteArray, originalName: String): String
    suspend fun readBytes(key: String): ByteArray
    suspend fun delete(key: String)

    /**
     * The current absolute filesystem path for [key] — resolved fresh every
     * call. For the media players and the image decoder only (they need a real
     * path); everything else works with the key.
     */
    fun absolutePathOf(key: String): String
}

// FileKit.filesDir is the one app-private directory that exists identically
// on Android/iOS/Desktop, so no expect/actual is needed here.
class FileKitAttachmentFileStore(private val newFileName: () -> String) : AttachmentFileStore {

    private val dir get() = FileKit.filesDir / DIR

    override suspend fun save(bytes: ByteArray, originalName: String): String {
        dir.createDirectories()
        val extension = originalName.substringAfterLast('.', missingDelimiterValue = "jpg")
        val key = "${newFileName()}.$extension"
        (dir / key).write(bytes)
        return key
    }

    override suspend fun readBytes(key: String): ByteArray = (dir / key).readBytes()

    override suspend fun delete(key: String) {
        val file = dir / key
        if (file.exists()) file.delete()
    }

    override fun absolutePathOf(key: String): String = (dir / key).path

    private companion object {
        const val DIR = "attachments"
    }
}
