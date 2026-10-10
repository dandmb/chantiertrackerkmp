package com.dmb.chantiertracker.data.local

import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrphanAttachmentFileCleaner(
    private val fileStore: AttachmentFileStore,
    private val attachmentDao: AttachmentDao,
    private val syncer: Syncer,
    private val scope: CoroutineScope,
    private val clock: Clock = SystemClock,
) {
    fun start(): Job = scope.launch { runCatching { removeOrphans() } }

    suspend fun removeOrphans(): List<String> = syncer.runExclusive {
        val writtenBefore = clock.nowEpochMillis() - RECENT_FILE_MILLIS
        val referenced = attachmentDao.referencedPaths().toSet()
        val orphans = fileStore.storedFiles()
            .filter { it.lastModifiedEpochMillis <= writtenBefore && it.key !in referenced }
            .map { it.key }
        orphans.forEach { fileStore.delete(it) }
        orphans
    }

    private companion object {
        const val RECENT_FILE_MILLIS = 10 * 60_000L
    }
}
