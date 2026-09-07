package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.AttachmentFileStore

class FakeAttachmentFileStore(newPath: (() -> String)? = null) : AttachmentFileStore {

    private var nextId = 0
    private val newPath: (() -> String)? = newPath
    private val files = mutableMapOf<String, ByteArray>()
    val deletedPaths = mutableListOf<String>()

    val storedPaths: Set<String> get() = files.keys

    override suspend fun save(bytes: ByteArray, originalName: String): String {
        val ext = originalName.substringAfterLast('.', missingDelimiterValue = "jpg")
        val path = newPath?.invoke() ?: "fake-attachments/${nextId++}.$ext"
        files[path] = bytes
        return path
    }

    override suspend fun readBytes(key: String): ByteArray =
        files[key] ?: error("No fake file at $key")

    override suspend fun delete(key: String) {
        files.remove(key)
        deletedPaths += key
    }

    // The tests treat the key as the "path" — good enough, no real filesystem.
    override fun absolutePathOf(key: String): String = key
}
