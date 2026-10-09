package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncIssueDao
import com.dmb.chantiertracker.data.local.db.SyncIssueRow
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.domain.repository.SyncIssueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSyncIssueDao : SyncIssueDao() {
    val rows = MutableStateFlow<List<SyncIssueRow>>(emptyList())
    val sentAgain = mutableListOf<Pair<SyncIssueTarget, String>>()
    val frozenAgain = mutableListOf<Triple<SyncIssueTarget, String, String?>>()

    override fun observeUnsettled(): Flow<List<SyncIssueRow>> = rows

    override suspend fun sendRefusedUpdateAgain(target: SyncIssueTarget, localId: String) {
        sentAgain += target to localId
        rows.value = rows.value.map { row ->
            if (row.target == target && row.localId == localId && row.lastSyncError == SyncError.UPDATE_REFUSED) {
                row.copy(syncStatus = SyncStatus.PENDING, lastSyncError = null, serverErrorCode = null)
            } else {
                row
            }
        }
    }

    override suspend fun freezeUnsentUpdateAgain(target: SyncIssueTarget, localId: String, serverErrorCode: String?) {
        frozenAgain += Triple(target, localId, serverErrorCode)
        rows.value = rows.value.map { row ->
            if (row.target == target && row.localId == localId && row.syncStatus == SyncStatus.PENDING && row.pendingOp == PendingOp.UPDATE) {
                row.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = serverErrorCode)
            } else {
                row
            }
        }
    }

    override suspend fun sendProjectUpdateAgain(localId: String) = Unit
    override suspend fun sendStageUpdateAgain(localId: String) = Unit
    override suspend fun sendMaterialUpdateAgain(localId: String) = Unit
    override suspend fun sendEntryUpdateAgain(localId: String) = Unit
    override suspend fun sendPurchaseLineUpdateAgain(localId: String) = Unit
    override suspend fun sendConsumptionLineUpdateAgain(localId: String) = Unit
    override suspend fun freezeProjectUpdateAgain(localId: String, serverErrorCode: String?) = Unit
    override suspend fun freezeStageUpdateAgain(localId: String, serverErrorCode: String?) = Unit
    override suspend fun freezeMaterialUpdateAgain(localId: String, serverErrorCode: String?) = Unit
    override suspend fun freezeEntryUpdateAgain(localId: String, serverErrorCode: String?) = Unit
    override suspend fun freezePurchaseLineUpdateAgain(localId: String, serverErrorCode: String?) = Unit
    override suspend fun freezeConsumptionLineUpdateAgain(localId: String, serverErrorCode: String?) = Unit
}

fun syncIssueRow(
    target: SyncIssueTarget,
    localId: String,
    syncStatus: SyncStatus = SyncStatus.CONFLICTED,
    pendingOp: PendingOp = PendingOp.CREATE,
    lastSyncError: String? = SyncError.REJECTED,
    serverErrorCode: String? = null,
    serverId: Long? = null,
    projectLocalId: String = "p1",
    projectName: String = "Villa Vidal",
    stageLocalId: String? = null,
    stageName: String? = null,
    dailyLogLocalId: String? = null,
    logDate: String? = null,
    entryType: String? = null,
    label: String? = null,
    unit: String? = null,
    quantity: Double? = null,
    serverQuantity: Double? = null,
) = SyncIssueRow(
    target = target,
    localId = localId,
    serverId = serverId,
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    lastSyncError = lastSyncError,
    serverErrorCode = serverErrorCode,
    projectLocalId = projectLocalId,
    projectName = projectName,
    stageLocalId = stageLocalId,
    stageName = stageName,
    dailyLogLocalId = dailyLogLocalId,
    logDate = logDate,
    entryType = entryType,
    label = label,
    unit = unit,
    quantity = quantity,
    serverQuantity = serverQuantity,
)

fun refusedIssue(reason: RefusalReason, serverCode: String? = reason.name, kind: SyncIssueKind = SyncIssueKind.REFUSED) =
    SyncIssue(kind, reason, serverCode)

fun issueItem(
    target: SyncIssueTarget,
    localId: String,
    issue: SyncIssue,
    projectLocalId: String = "p1",
    projectName: String = "Villa Vidal",
    stageLocalId: String? = null,
    stageName: String? = null,
    dailyLogLocalId: String? = null,
    date: String? = null,
    entryType: EntryType? = null,
    label: String? = null,
    unit: String? = null,
    quantity: Double? = null,
    serverQuantity: Double? = null,
    blockedBy: com.dmb.chantiertracker.domain.model.SyncIssueParent? = null,
) = SyncIssueItem(
    target = target,
    localId = localId,
    issue = issue,
    projectLocalId = projectLocalId,
    projectName = projectName,
    stageLocalId = stageLocalId,
    stageName = stageName,
    dailyLogLocalId = dailyLogLocalId,
    date = date,
    entryType = entryType,
    label = label,
    unit = unit,
    quantity = quantity,
    serverQuantity = serverQuantity,
    blockedBy = blockedBy,
)

class FakeSyncIssueRepository(items: List<SyncIssueItem> = emptyList()) : SyncIssueRepository {
    val items = MutableStateFlow(items)
    val retried = mutableListOf<SyncIssueItem>()
    var retryOutcome = RetryOutcome.ACCEPTED
    var retryGate: CompletableDeferred<Unit>? = null

    override fun observeIssues(): Flow<List<SyncIssueItem>> = items

    override fun observeIssueCount(): Flow<Int> = items.map { all -> all.count { it.issue.kind != SyncIssueKind.BLOCKED_BY_PARENT } }

    override suspend fun retry(item: SyncIssueItem): RetryOutcome {
        retried += item
        retryGate?.await()
        if (retryOutcome == RetryOutcome.ACCEPTED) items.value = items.value - item
        return retryOutcome
    }
}
