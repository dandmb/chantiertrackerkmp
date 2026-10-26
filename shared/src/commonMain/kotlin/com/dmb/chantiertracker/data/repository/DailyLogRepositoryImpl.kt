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
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.syncIssue
import com.dmb.chantiertracker.domain.model.CreatedDailyEntry
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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
            entryDao.observeBlockedByParent(),
        ) { log, entries, blocked ->
            log?.let {
                DailyLogDetail(
                    localId = it.localId,
                    stageLocalId = it.stageLocalId,
                    date = it.date,
                    entries = entries.map { entry -> entry.toDailyEntry(blockedByParent = entry.localId in blocked) },
                )
            }
        }

    override fun observeEntry(entryLocalId: String): Flow<DailyEntry?> =
        combine(entryDao.observeEntry(entryLocalId), entryDao.observeBlockedByParent()) { entry, blocked ->
            entry?.toDailyEntry(blockedByParent = entry.localId in blocked)
        }

    override suspend fun createPurchaseEntry(stageLocalId: String, date: String): CreatedDailyEntry =
        createEntry(stageLocalId, date, EntryType.PURCHASE)

    override suspend fun createWorkEntry(stageLocalId: String, date: String): CreatedDailyEntry =
        createEntry(stageLocalId, date, EntryType.WORK)

    private suspend fun createEntry(stageLocalId: String, date: String, type: EntryType): CreatedDailyEntry {
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
        if (existing != null && existing.pendingOp != PendingOp.DELETE) return CreatedDailyEntry(log.localId, existing.localId)

        val entryLocalId = newLocalId()
        entryDao.insertNew(
            DailyEntryEntity(
                localId = entryLocalId,
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
        return CreatedDailyEntry(log.localId, entryLocalId)
    }

    override suspend fun updateEntry(entryLocalId: String, summary: String) {
        entryDao.changeLocally(entryLocalId) { existing ->
            existing.copy(
                summary = summary.ifBlank { null },
                syncStatus = SyncStatus.PENDING,
                pendingOp = if (existing.pendingOp == PendingOp.CREATE) PendingOp.CREATE else PendingOp.UPDATE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
                serverErrorCode = null,
            )
        } ?: return
        syncer.requestSync()
    }

    // A stage's day list needs the stage's log *summaries* (existence + server
    // id per day); syncStage pulls those alongside the stage itself (ADR-30).
    override suspend fun refreshLogs(stageLocalId: String) {
        syncer.syncStage(stageLocalId)
    }

    // The day screen needs the full hierarchy — entries, lines, photos — which
    // is what syncLog pulls (ADR-30).
    override suspend fun refreshLog(logLocalId: String) {
        syncer.syncLog(logLocalId)
    }
}

internal fun String.toEntryType(): EntryType = when (uppercase()) {
    "PURCHASE" -> EntryType.PURCHASE
    "WORK" -> EntryType.WORK
    else -> EntryType.UNKNOWN
}

internal fun DailyEntryEntity.toDailyEntry(blockedByParent: Boolean = false): DailyEntry = DailyEntry(
    localId = localId,
    dailyLogLocalId = dailyLogLocalId,
    type = type.toEntryType(),
    summary = summary,
    syncIssue = syncIssue(blockedByParent),
)
