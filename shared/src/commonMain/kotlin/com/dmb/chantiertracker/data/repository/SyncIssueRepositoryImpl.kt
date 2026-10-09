package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.SyncIssueDao
import com.dmb.chantiertracker.data.local.db.SyncIssueRow
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.syncIssue
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueParent
import com.dmb.chantiertracker.domain.model.countsToReview
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.canBeRetried
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.domain.repository.SyncIssueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SyncIssueRepositoryImpl(
    private val syncIssueDao: SyncIssueDao,
    stageDao: StageDao,
    materialDao: MaterialDao,
    dailyEntryDao: DailyEntryDao,
    purchaseLineDao: PurchaseLineDao,
    consumptionLineDao: ConsumptionLineDao,
    attachmentDao: AttachmentDao,
    private val syncer: Syncer,
) : SyncIssueRepository {

    private val blockedByParent: Flow<Set<Pair<SyncIssueTarget, String>>> = combine(
        stageDao.observeBlockedByParent().map { ids -> ids.map { SyncIssueTarget.STAGE to it } },
        materialDao.observeBlockedByParent().map { ids -> ids.map { SyncIssueTarget.MATERIAL to it } },
        dailyEntryDao.observeBlockedByParent().map { ids -> ids.map { SyncIssueTarget.ENTRY to it } },
        purchaseLineDao.observeBlockedByParent().map { ids -> ids.map { SyncIssueTarget.PURCHASE_LINE to it } },
        consumptionLineDao.observeBlockedByParent().map { ids -> ids.map { SyncIssueTarget.CONSUMPTION_LINE to it } },
        attachmentDao.observeBlockedByParent().map { ids -> ids.map { SyncIssueTarget.ATTACHMENT to it } },
    ) { perTable -> perTable.flatMap { it }.toSet() }

    override fun observeIssues(): Flow<List<SyncIssueItem>> =
        combine(syncIssueDao.observeUnsettled(), blockedByParent) { rows, blocked ->
            rows.mapNotNull { row ->
                row.syncIssue(blockedByParent = (row.target to row.localId) in blocked)?.let { issue ->
                    row.toItem(issue, blockedBy = if (issue.kind == SyncIssueKind.BLOCKED_BY_PARENT) row.refusedParentAmong(rows) else null)
                }
            }
        }

    override fun observeIssueCount(): Flow<Int> = observeIssues().map { items -> items.count { it.issue.countsToReview } }

    override suspend fun retry(item: SyncIssueItem): RetryOutcome {
        if (!item.issue.canBeRetried) return RetryOutcome.STILL_REFUSED
        val frozenUpdate = item.issue.kind == SyncIssueKind.UPDATE_REFUSED
        if (frozenUpdate) syncIssueDao.sendRefusedUpdateAgain(item.target, item.localId)
        if (syncer.syncNow() != SyncOutcome.Synced) {
            if (frozenUpdate) syncIssueDao.freezeUnsentUpdateAgain(item.target, item.localId, item.issue.serverCode)
            return RetryOutcome.NOT_SENT
        }
        val stillListed = observeIssues().first().any { it.key == item.key }
        return if (stillListed) RetryOutcome.STILL_REFUSED else RetryOutcome.ACCEPTED
    }
}

private fun SyncIssueRow.neverReachedTheServer(): Boolean = syncStatus == SyncStatus.CONFLICTED && serverId == null

private fun SyncIssueRow.refusedParentAmong(rows: List<SyncIssueRow>): SyncIssueParent? {
    val refused = rows.filter { it.neverReachedTheServer() }
    val entry = refused.firstOrNull {
        it.target == SyncIssueTarget.ENTRY && dependsOnAnEntry() && it.dailyLogLocalId == dailyLogLocalId && it.entryType == entryType
    }
    val stage = refused.firstOrNull { it.target == SyncIssueTarget.STAGE && target != SyncIssueTarget.MATERIAL && it.localId == stageLocalId }
    val material = refused.firstOrNull {
        it.target == SyncIssueTarget.MATERIAL && dependsOnAMaterial() && it.projectLocalId == projectLocalId && it.label == label
    }
    val project = refused.firstOrNull { it.target == SyncIssueTarget.PROJECT && it.localId == projectLocalId }
    return when {
        entry != null -> SyncIssueParent(SyncIssueTarget.ENTRY, entryType = entry.entryType.toEntryType(), date = entry.logDate)
        stage != null -> SyncIssueParent(SyncIssueTarget.STAGE, name = stage.stageName)
        material != null -> SyncIssueParent(SyncIssueTarget.MATERIAL, name = material.label)
        project != null -> SyncIssueParent(SyncIssueTarget.PROJECT, name = project.projectName)
        else -> null
    }
}

private fun SyncIssueRow.dependsOnAnEntry(): Boolean =
    target == SyncIssueTarget.PURCHASE_LINE || target == SyncIssueTarget.CONSUMPTION_LINE || target == SyncIssueTarget.ATTACHMENT

private fun SyncIssueRow.dependsOnAMaterial(): Boolean =
    target == SyncIssueTarget.PURCHASE_LINE || target == SyncIssueTarget.CONSUMPTION_LINE

private fun String?.toEntryType(): EntryType? = this?.let { type -> EntryType.entries.firstOrNull { it.name == type } ?: EntryType.UNKNOWN }

private fun SyncIssueRow.toItem(issue: SyncIssue, blockedBy: SyncIssueParent?) = SyncIssueItem(
    target = target,
    localId = localId,
    issue = issue,
    projectLocalId = projectLocalId,
    projectName = projectName,
    stageLocalId = stageLocalId,
    stageName = stageName,
    dailyLogLocalId = dailyLogLocalId,
    date = logDate,
    entryType = entryType.toEntryType(),
    label = label,
    unit = unit,
    quantity = quantity,
    serverQuantity = serverQuantity,
    blockedBy = blockedBy,
)
