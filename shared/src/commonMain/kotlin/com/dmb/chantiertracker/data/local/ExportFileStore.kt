package com.dmb.chantiertracker.data.local

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write

// A project PDF export is a throwaway file — generated on demand, handed to the
// platform share sheet once, then disposable. It lives in `FileKit.cacheDir`
// (the OS may evict it), never `filesDir` (that's for durable attachments).
// FileKit's own FileProvider (Android) covers the cache path, so a file here is
// shareable without touching the app manifest. Kept as an interface so the
// repository test substitutes an in-memory fake.
interface ExportFileStore {
    /**
     * Writes [bytes] under [fileName], replacing any previous export, and
     * returns the absolute path.
     */
    suspend fun save(bytes: ByteArray, fileName: String): String
}

// `FileKit.cacheDir` exists identically on Android/iOS/Desktop — no
// expect/actual, same as `FileKitAttachmentFileStore`.
class FileKitExportFileStore : ExportFileStore {

    private val dir get() = FileKit.cacheDir / DIR

    override suspend fun save(bytes: ByteArray, fileName: String): String {
        dir.createDirectories()
        // One export at a time — older files are just clutter in the cache.
        dir.list().forEach { it.delete(mustExist = false) }
        val file = dir / fileName
        file.write(bytes)
        return file.path
    }

    private companion object {
        const val DIR = "exports"
    }
}
