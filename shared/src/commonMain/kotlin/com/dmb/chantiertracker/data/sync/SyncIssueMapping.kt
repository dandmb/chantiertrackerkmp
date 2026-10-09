package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.SyncedRow
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.refusalReasonOf

fun SyncedRow.syncIssue(blockedByParent: Boolean = false): SyncIssue? = when {
    holdsDeleteRefusal() -> refused(SyncIssueKind.DELETE_REFUSED)
    syncStatus == SyncStatus.SYNCED -> null
    syncStatus == SyncStatus.PENDING -> SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT).takeIf { blockedByParent }
    lastSyncError == SyncError.DELETED_ON_SERVER -> SyncIssue(SyncIssueKind.DELETED_ON_SERVER)
    lastSyncError == SyncError.PLAN_LIMIT -> SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PLAN_LIMIT, serverErrorCode)
    lastSyncError == SyncError.FILE_REFUSED -> SyncIssue(SyncIssueKind.REFUSED, RefusalReason.FILE_REFUSED, serverErrorCode)
    lastSyncError == SyncError.UPDATE_REFUSED -> refused(SyncIssueKind.UPDATE_REFUSED)
    pendingOp == PendingOp.UPDATE && serverId != null -> refused(SyncIssueKind.UPDATE_REFUSED)
    else -> refused(SyncIssueKind.REFUSED)
}

private fun SyncedRow.refused(kind: SyncIssueKind) = SyncIssue(kind, refusalReasonOf(serverErrorCode), serverErrorCode)
