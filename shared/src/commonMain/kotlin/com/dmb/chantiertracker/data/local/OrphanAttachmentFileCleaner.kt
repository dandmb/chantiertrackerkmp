package com.dmb.chantiertracker.data.local

import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.sync.Syncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrphanAttachmentFileCleaner(
    private val fileStore: AttachmentFileStore,
    private val attachmentDao: AttachmentDao,
    private val syncer: Syncer,
    private val scope: CoroutineScope,
) {
    fun start(): Job = scope.launch { runCatching { removeOrphans() } }

    suspend fun removeOrphans(): List<String> = syncer.runExclusive {
        val stored = fileStore.storedKeys()
        val referenced = attachmentDao.referencedPaths().toSet()
        val orphans = stored.filterNot { it in referenced }
        orphans.forEach { fileStore.delete(it) }
        orphans
    }
}
