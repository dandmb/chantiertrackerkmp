package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.AttachmentFileStore

class FakeAttachmentFileStore(newPath: (() -> String)? = null) : AttachmentFileStore {

    private var nextId = 0
    private val newPath: () -> String = newPath ?: { "fake-attachments/${nextId++}.jpg" }
    private val files = mutableMapOf<String, ByteArray>()
    val deletedPaths = mutableListOf<String>()

    val storedPaths: Set<String> get() = files.keys

    override suspend fun save(bytes: ByteArray, originalName: String): String {
        val path = newPath()
        files[path] = bytes
        return path
    }

    override suspend fun readBytes(path: String): ByteArray =
        files[path] ?: error("No fake file at $path")

    override suspend fun delete(path: String) {
        files.remove(path)
        deletedPaths += path
    }
}
