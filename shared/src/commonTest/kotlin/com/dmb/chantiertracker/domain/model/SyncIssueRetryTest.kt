package com.dmb.chantiertracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncIssueRetryTest {

    private val dependingOnSomethingElse = setOf(
        RefusalReason.PLAN_LIMIT,
        RefusalReason.PROJECT_OR_STAGE_INACTIVE,
        RefusalReason.INSUFFICIENT_ROLE,
    )

    @Test
    fun only_the_refusals_that_depend_on_something_else_can_be_retried() {
        assertEquals(dependingOnSomethingElse, RefusalReason.entries.filter { it.dependsOnSomethingElse }.toSet())
    }

    @Test
    fun a_refused_creation_and_a_refused_update_can_be_retried_when_the_reason_allows_it() {
        dependingOnSomethingElse.forEach { reason ->
            assertTrue(SyncIssue(SyncIssueKind.REFUSED, reason).canBeRetried, "$reason, creation")
            assertTrue(SyncIssue(SyncIssueKind.UPDATE_REFUSED, reason).canBeRetried, "$reason, update")
        }
        assertFalse(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.INSUFFICIENT_STOCK).canBeRetried)
        assertFalse(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.UNKNOWN).canBeRetried)
        assertFalse(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.FILE_REFUSED).canBeRetried)
    }

    @Test
    fun a_day_that_is_past_stays_past_so_its_refusal_is_never_retried() {
        assertFalse(RefusalReason.ENTRY_DATE_RESTRICTED.dependsOnSomethingElse)
        assertFalse(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.ENTRY_DATE_RESTRICTED).canBeRetried, "creation")
        assertFalse(SyncIssue(SyncIssueKind.UPDATE_REFUSED, RefusalReason.ENTRY_DATE_RESTRICTED).canBeRetried, "update")
    }

    @Test
    fun a_refused_delete_a_row_gone_on_the_server_and_a_waiting_child_are_never_retried() {
        assertFalse(SyncIssue(SyncIssueKind.DELETE_REFUSED, RefusalReason.INSUFFICIENT_ROLE).canBeRetried)
        assertFalse(SyncIssue(SyncIssueKind.DELETED_ON_SERVER).canBeRetried)
        assertFalse(SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT).canBeRetried)
    }
}
