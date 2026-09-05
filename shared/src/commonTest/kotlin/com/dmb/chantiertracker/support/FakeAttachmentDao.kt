package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.AttachmentEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAttachmentDao(initial: List<AttachmentEntity> = emptyList()) : AttachmentDao {

    private val rows = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<AttachmentEntity> get() = rows.value.values.toList()

    override fun observeForEntry(entryLocalId: String): Flow<List<AttachmentEntity>> =
        rows.map { r -> r.values.filter { it.entryLocalId == entryLocalId && it.pendingOp != PendingOp.DELETE } }

    override suspend fun findByLocalId(localId: String): AttachmentEntity? = rows.value[localId]

    override suspend fun findByServerId(serverId: Long): AttachmentEntity? =
        rows.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<AttachmentEntity> =
        rows.value.values.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun upsert(attachment: AttachmentEntity) {
        rows.value = rows.value + (attachment.localId to attachment)
    }

    override suspend fun deleteByLocalId(localId: String) {
        rows.value = rows.value - localId
    }
}
