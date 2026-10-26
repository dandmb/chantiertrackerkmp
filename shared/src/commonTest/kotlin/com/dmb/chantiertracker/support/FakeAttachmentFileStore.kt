package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.StoredAttachmentFile
import kotlinx.io.readByteArray

class FakeAttachmentFileStore(newPath: (() -> String)? = null) : AttachmentFileStore {

    private var nextId = 0
    private val newPath: (() -> String)? = newPath
    private val files = mutableMapOf<String, ByteArray>()
    private val savedAt = mutableMapOf<String, Long>()
    var now = 0L
    val deletedPaths = mutableListOf<String>()

    val storedPaths: Set<String> get() = files.keys

    override suspend fun save(bytes: ByteArray, originalName: String): String {
        val ext = originalName.substringAfterLast('.', missingDelimiterValue = "jpg")
        val path = newPath?.invoke() ?: "fake-attachments/${nextId++}.$ext"
        files[path] = bytes
        savedAt[path] = now
        return path
    }

    override suspend fun readBytes(key: String): ByteArray {
        readBytesCount++
        return files[key] ?: error("No fake file at $key")
    }

    var failOnDelete = false

    override suspend fun delete(key: String) {
        if (failOnDelete) error("the process died before the file was deleted")
        files.remove(key)
        deletedPaths += key
    }

    override suspend fun storedFiles(): List<StoredAttachmentFile> = files.keys.map { StoredAttachmentFile(it, savedAt.getValue(it)) }

    var deleteAllCount = 0
        private set

    override suspend fun deleteAll() {
        deleteAllCount++
        deletedPaths += files.keys
        files.clear()
    }

    val sourcesOpened = mutableListOf<String>()
    var readBytesCount = 0
        private set

    var beforeSavingFrom: (suspend () -> Unit)? = null

    override suspend fun saveFrom(source: kotlinx.io.RawSource, originalName: String): String {
        beforeSavingFrom?.invoke()
        val ext = originalName.substringAfterLast('.', missingDelimiterValue = "jpg")
        val path = newPath?.invoke() ?: "fake-attachments/${nextId++}.$ext"
        files[path] = ByteArray(0)
        savedAt[path] = now
        val chunk = kotlinx.io.Buffer()
        source.use {
            while (it.readAtMostTo(chunk, 1_024) != -1L) {
                files[path] = files.getValue(path) + chunk.readByteArray()
                savedAt[path] = now
            }
        }
        return path
    }

    override fun source(key: String): kotlinx.io.RawSource {
        sourcesOpened += key
        return kotlinx.io.Buffer().apply { write(files[key] ?: error("No fake file at $key")) }
    }

    override suspend fun sizeOf(key: String): Long = (files[key] ?: error("No fake file at $key")).size.toLong()

    // The tests treat the key as the "path" — good enough, no real filesystem.
    override fun absolutePathOf(key: String): String = key
}
