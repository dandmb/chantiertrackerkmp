package com.dmb.chantiertracker.data.local

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write

// Attachments are the first entity in this app whose payload is a binary
// blob, not JSON — Room stores only a path, never the bytes (see
// AttachmentEntity). Kept as an interface, like TokenStorage/OnboardingStore,
// so repository tests can substitute an in-memory fake instead of touching
// the real filesystem.
interface AttachmentFileStore {
    suspend fun save(bytes: ByteArray, originalName: String): String
    suspend fun readBytes(path: String): ByteArray
    suspend fun delete(path: String)
}

// FileKit.filesDir is the one app-private directory that exists identically
// on Android/iOS/Desktop, so no expect/actual is needed here.
class FileKitAttachmentFileStore(private val newFileName: () -> String) : AttachmentFileStore {

    override suspend fun save(bytes: ByteArray, originalName: String): String {
        val dir = FileKit.filesDir / "attachments"
        dir.createDirectories()
        val extension = originalName.substringAfterLast('.', missingDelimiterValue = "jpg")
        val file = dir / "${newFileName()}.$extension"
        file.write(bytes)
        return file.absolutePath()
    }

    override suspend fun readBytes(path: String): ByteArray = PlatformFile(path).readBytes()

    override suspend fun delete(path: String) {
        val file = PlatformFile(path)
        if (file.exists()) file.delete()
    }
}
