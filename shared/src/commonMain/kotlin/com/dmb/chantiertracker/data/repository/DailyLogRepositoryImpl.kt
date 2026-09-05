@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.DailyEntryEntity
import com.dmb.chantiertracker.data.local.db.DailyLogDao
import com.dmb.chantiertracker.data.local.db.DailyLogEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DailyLogRepositoryImpl(
    private val logDao: DailyLogDao,
    private val entryDao: DailyEntryDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : DailyLogRepository {

    override fun observeLogs(stageLocalId: String): Flow<List<DailyLog>> =
        combine(
            logDao.observeLogsForStage(stageLocalId),
            entryDao.observeEntriesForStage(stageLocalId),
        ) { logs, entries ->
            val entriesByLog = entries.groupBy { it.dailyLogLocalId }
            logs.map { log ->
                val logEntries = entriesByLog[log.localId].orEmpty()
                DailyLog(
                    localId = log.localId,
                    stageLocalId = log.stageLocalId,
                    date = log.date,
                    hasPurchase = logEntries.any { it.type == EntryType.PURCHASE.name },
                    hasWork = logEntries.any { it.type == EntryType.WORK.name },
                )
            }
        }

    override fun observeLog(logLocalId: String): Flow<DailyLogDetail?> =
        combine(
            logDao.observeLog(logLocalId),
            entryDao.observeEntriesForLog(logLocalId),
        ) { log, entries ->
            log?.let {
                DailyLogDetail(
                    localId = it.localId,
                    stageLocalId = it.stageLocalId,
                    date = it.date,
                    entries = entries.map(DailyEntryEntity::toDailyEntry),
                )
            }
        }

    override suspend fun createPurchaseEntry(stageLocalId: String, date: String): String =
        createEntry(stageLocalId, date, EntryType.PURCHASE)

    override suspend fun createWorkEntry(stageLocalId: String, date: String): String =
        createEntry(stageLocalId, date, EntryType.WORK)

    private suspend fun createEntry(stageLocalId: String, date: String, type: EntryType): String {
        val now = clock.nowEpochMillis()
        val log = logDao.findByStageAndDate(stageLocalId, date) ?: DailyLogEntity(
            localId = newLocalId(),
            serverId = null,
            stageLocalId = stageLocalId,
            date = date,
            locallyCreatedAt = now,
            lastSyncedAt = null,
        ).also { logDao.upsert(it) }

        val existing = entryDao.findByLogAndType(log.localId, type.name)
        if (existing != null && existing.pendingOp != PendingOp.DELETE) return log.localId

        entryDao.upsert(
            DailyEntryEntity(
                localId = newLocalId(),
                serverId = null,
                dailyLogLocalId = log.localId,
                type = type.name,
                summary = null,
                createdById = null,
                createdAt = null,
                modifiedById = null,
                modifiedAt = null,
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.CREATE,
                locallyModifiedAt = now,
                lastSyncedAt = null,
                remoteUpdatedAt = null,
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
        return log.localId
    }

    override suspend fun updateEntry(entryLocalId: String, summary: String) {
        val existing = entryDao.findByLocalId(entryLocalId) ?: return
        entryDao.upsert(
            existing.copy(
                summary = summary.ifBlank { null },
                syncStatus = SyncStatus.PENDING,
                pendingOp = if (existing.pendingOp == PendingOp.CREATE) PendingOp.CREATE else PendingOp.UPDATE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }

    // Not yet taught to SyncEngine (Step 4) — a harmless generic sync pass for
    // now, so screens can already call refresh unconditionally; once
    // SyncEngine pulls logs/entries these calls start doing real work without
    // any change here.
    override suspend fun refreshLogs(stageLocalId: String) {
        syncer.syncNow()
    }

    override suspend fun refreshLog(logLocalId: String) {
        syncer.syncNow()
    }
}

internal fun String.toEntryType(): EntryType = when (uppercase()) {
    "PURCHASE" -> EntryType.PURCHASE
    "WORK" -> EntryType.WORK
    else -> EntryType.UNKNOWN
}

internal fun DailyEntryEntity.toDailyEntry(): DailyEntry = DailyEntry(
    localId = localId,
    dailyLogLocalId = dailyLogLocalId,
    type = type.toEntryType(),
    summary = summary,
)
