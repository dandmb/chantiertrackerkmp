package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.refusalReasonOf
import com.dmb.chantiertracker.support.localPurchaseLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SyncIssueMappingTest {

    private fun line(
        syncStatus: SyncStatus,
        pendingOp: PendingOp,
        lastSyncError: String? = null,
        serverErrorCode: String? = null,
        serverId: Long? = null,
    ) = localPurchaseLine("pl", serverId = serverId, pendingOp = pendingOp, syncStatus = syncStatus)
        .copy(lastSyncError = lastSyncError, serverErrorCode = serverErrorCode)

    @Test
    fun a_synced_row_and_a_row_simply_waiting_to_be_sent_have_no_issue() {
        assertNull(line(SyncStatus.SYNCED, PendingOp.NONE, serverId = 1).syncIssue())
        assertNull(line(SyncStatus.PENDING, PendingOp.CREATE).syncIssue())
        assertNull(line(SyncStatus.PENDING, PendingOp.UPDATE, serverId = 1).syncIssue())
    }

    @Test
    fun a_row_awaiting_its_server_version_is_not_an_issue_and_holds_no_refusal() {
        val awaiting = line(SyncStatus.SYNCED, PendingOp.NONE, serverId = 1).copy(awaitsServerVersion = true)

        assertNull(awaiting.syncIssue())
        assertNull(awaiting.syncIssue(blockedByParent = true))
        assertFalse(awaiting.holdsDeleteRefusal())
        assertNull(awaiting.keptDeleteRefusal(), "the next pull clears the note instead of carrying it as a refusal")
        assertNull(awaiting.copy(serverErrorCode = "PROJECT_INSUFFICIENT_ROLE").keptDeleteRefusalCode())
    }

    @Test
    fun a_refused_creation_carries_the_reason_the_server_gave() {
        assertEquals(
            SyncIssue(SyncIssueKind.REFUSED, RefusalReason.INSUFFICIENT_STOCK, "INSUFFICIENT_STOCK"),
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.REJECTED, "INSUFFICIENT_STOCK").syncIssue(),
        )
        assertEquals(
            SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PLAN_LIMIT, "PLAN_LIMIT_EXCEEDED"),
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.PLAN_LIMIT, "PLAN_LIMIT_EXCEEDED").syncIssue(),
        )
    }

    @Test
    fun a_row_refused_before_codes_were_kept_reads_as_refused_for_an_unknown_reason() {
        assertEquals(
            SyncIssue(SyncIssueKind.REFUSED, RefusalReason.UNKNOWN, null),
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.REJECTED, serverErrorCode = null).syncIssue(),
        )
        assertEquals(
            SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PLAN_LIMIT, null),
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.PLAN_LIMIT, serverErrorCode = null).syncIssue(),
        )
        assertEquals(
            SyncIssue(SyncIssueKind.UPDATE_REFUSED, RefusalReason.UNKNOWN, null),
            line(SyncStatus.CONFLICTED, PendingOp.UPDATE, SyncError.REJECTED, serverErrorCode = null, serverId = 9).syncIssue(),
        )
    }

    @Test
    fun a_refused_update_a_refused_delete_a_refused_file_and_a_row_gone_on_the_server_are_told_apart() {
        assertEquals(
            SyncIssue(SyncIssueKind.UPDATE_REFUSED, RefusalReason.STOCK_CONSUMED, "STOCK_CONSUMED"),
            line(SyncStatus.CONFLICTED, PendingOp.UPDATE, SyncError.UPDATE_REFUSED, "STOCK_CONSUMED", serverId = 9).syncIssue(),
        )
        assertEquals(
            SyncIssue(SyncIssueKind.DELETE_REFUSED, RefusalReason.INSUFFICIENT_ROLE, "PROJECT_INSUFFICIENT_ROLE"),
            line(SyncStatus.SYNCED, PendingOp.NONE, SyncError.REJECTED, "PROJECT_INSUFFICIENT_ROLE", serverId = 9).syncIssue(),
        )
        assertEquals(
            SyncIssue(SyncIssueKind.REFUSED, RefusalReason.FILE_REFUSED, "ATTACHMENT_TOO_LARGE"),
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.FILE_REFUSED, "ATTACHMENT_TOO_LARGE").syncIssue(),
        )
        assertEquals(
            SyncIssue(SyncIssueKind.DELETED_ON_SERVER),
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.DELETED_ON_SERVER, "PROJECT_INSUFFICIENT_ROLE").syncIssue(),
        )
    }

    @Test
    fun only_a_row_waiting_to_be_sent_can_be_blocked_by_its_parent() {
        assertEquals(SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT), line(SyncStatus.PENDING, PendingOp.CREATE).syncIssue(blockedByParent = true))
        assertNull(line(SyncStatus.SYNCED, PendingOp.NONE, serverId = 1).syncIssue(blockedByParent = true))
        assertEquals(
            SyncIssueKind.REFUSED,
            line(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.REJECTED, "DUPLICATE_ENTRY").syncIssue(blockedByParent = true)?.kind,
        )
    }

    @Test
    fun every_known_server_code_has_a_reason_and_any_other_is_unknown() {
        val expected = mapOf(
            "PLAN_LIMIT_EXCEEDED" to RefusalReason.PLAN_LIMIT,
            "PROJECT_OR_STAGE_INACTIVE" to RefusalReason.PROJECT_OR_STAGE_INACTIVE,
            "ENTRY_DATE_RESTRICTED" to RefusalReason.ENTRY_DATE_RESTRICTED,
            "PROJECT_INSUFFICIENT_ROLE" to RefusalReason.INSUFFICIENT_ROLE,
            "INSUFFICIENT_STOCK" to RefusalReason.INSUFFICIENT_STOCK,
            "STOCK_CONSUMED" to RefusalReason.STOCK_CONSUMED,
            "STOCK_RELEASE_BLOCKED" to RefusalReason.STOCK_CONSUMED,
            "DUPLICATE_ENTRY" to RefusalReason.DUPLICATE_ENTRY,
            "DUPLICATE_MATERIAL" to RefusalReason.DUPLICATE_MATERIAL,
            "VALIDATION_FAILED" to RefusalReason.INVALID_VALUE,
            "INVALID_AMOUNT" to RefusalReason.INVALID_VALUE,
            "ATTACHMENT_TOO_LARGE" to RefusalReason.FILE_REFUSED,
            "INVALID_ATTACHMENT_TYPE" to RefusalReason.FILE_REFUSED,
            "UNSUPPORTED_IMAGE" to RefusalReason.FILE_REFUSED,
            "VIDEO_TOO_LONG" to RefusalReason.FILE_REFUSED,
            "LOCAL_FILE_MISSING" to RefusalReason.FILE_REFUSED,
            "A_CODE_THIS_APP_DOES_NOT_KNOW" to RefusalReason.UNKNOWN,
        )
        expected.forEach { (code, reason) -> assertEquals(reason, refusalReasonOf(code), code) }
        assertEquals(RefusalReason.UNKNOWN, refusalReasonOf(null))
    }
}
