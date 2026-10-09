package com.dmb.chantiertracker.domain.model

import com.dmb.chantiertracker.domain.model.SyncIssueAction.ACKNOWLEDGE
import com.dmb.chantiertracker.domain.model.SyncIssueAction.DISCARD
import com.dmb.chantiertracker.domain.model.SyncIssueAction.FIX
import com.dmb.chantiertracker.domain.model.SyncIssueAction.RETRY
import com.dmb.chantiertracker.domain.model.SyncIssueAction.REVERT
import com.dmb.chantiertracker.support.issueItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncIssueActionsTest {

    private val everyTarget = SyncIssueTarget.entries
    private val withAForm = setOf(SyncIssueTarget.PROJECT, SyncIssueTarget.STAGE, SyncIssueTarget.ENTRY, SyncIssueTarget.PURCHASE_LINE, SyncIssueTarget.CONSUMPTION_LINE)
    private val dependingOnSomethingElse =
        setOf(RefusalReason.PLAN_LIMIT, RefusalReason.PROJECT_OR_STAGE_INACTIVE, RefusalReason.ENTRY_DATE_RESTRICTED, RefusalReason.INSUFFICIENT_ROLE)
    private val liftedByACorrection = setOf(RefusalReason.INSUFFICIENT_STOCK, RefusalReason.STOCK_CONSUMED, RefusalReason.INVALID_VALUE)

    private fun actionsOf(target: SyncIssueTarget, kind: SyncIssueKind, reason: RefusalReason? = null) =
        issueItem(target, "x", SyncIssue(kind, reason)).actions

    @Test
    fun a_child_waiting_on_a_refused_parent_has_no_action_of_its_own() {
        everyTarget.forEach { assertEquals(emptyList(), actionsOf(it, SyncIssueKind.BLOCKED_BY_PARENT), "$it") }
    }

    @Test
    fun what_was_deleted_on_the_server_and_a_refused_delete_can_only_be_acknowledged() {
        everyTarget.forEach { target ->
            assertEquals(listOf(ACKNOWLEDGE), actionsOf(target, SyncIssueKind.DELETED_ON_SERVER), "$target, deleted on server")
            RefusalReason.entries.forEach { reason ->
                assertEquals(listOf(ACKNOWLEDGE), actionsOf(target, SyncIssueKind.DELETE_REFUSED, reason), "$target, refused delete, $reason")
            }
        }
    }

    @Test
    fun a_refused_creation_that_depends_on_something_else_can_be_retried_or_discarded() {
        everyTarget.forEach { target ->
            dependingOnSomethingElse.forEach { reason ->
                assertEquals(listOf(RETRY, DISCARD), actionsOf(target, SyncIssueKind.REFUSED, reason), "$target, $reason")
            }
        }
    }

    @Test
    fun a_refused_creation_a_correction_can_lift_can_be_fixed_where_a_form_exists_or_discarded() {
        everyTarget.forEach { target ->
            liftedByACorrection.forEach { reason ->
                val expected = if (target in withAForm) listOf(FIX, DISCARD) else listOf(DISCARD)
                assertEquals(expected, actionsOf(target, SyncIssueKind.REFUSED, reason), "$target, $reason")
            }
        }
    }

    @Test
    fun a_duplicate_and_a_refused_file_can_only_be_discarded() {
        everyTarget.forEach { target ->
            listOf(RefusalReason.DUPLICATE_ENTRY, RefusalReason.DUPLICATE_MATERIAL, RefusalReason.FILE_REFUSED).forEach { reason ->
                assertEquals(listOf(DISCARD), actionsOf(target, SyncIssueKind.REFUSED, reason), "$target, $reason")
            }
        }
    }

    @Test
    fun a_creation_refused_for_an_unknown_reason_can_always_be_acknowledged_and_fixed_where_a_form_exists() {
        everyTarget.forEach { target ->
            val expected = if (target in withAForm) listOf(FIX, ACKNOWLEDGE) else listOf(ACKNOWLEDGE)
            assertEquals(expected, actionsOf(target, SyncIssueKind.REFUSED, RefusalReason.UNKNOWN), "$target, unknown")
            assertEquals(expected, actionsOf(target, SyncIssueKind.REFUSED, null), "$target, no reason")
        }
    }

    @Test
    fun a_refused_change_can_always_be_reverted_and_is_never_discarded() {
        everyTarget.forEach { target ->
            RefusalReason.entries.forEach { reason ->
                val actions = actionsOf(target, SyncIssueKind.UPDATE_REFUSED, reason)
                assertEquals(REVERT, actions.last(), "$target, $reason")
                assertFalse(DISCARD in actions || ACKNOWLEDGE in actions, "$target, $reason: the element exists on the server")
            }
            dependingOnSomethingElse.forEach { assertEquals(listOf(RETRY, REVERT), actionsOf(target, SyncIssueKind.UPDATE_REFUSED, it), "$target, $it") }
            (liftedByACorrection + RefusalReason.UNKNOWN).forEach { reason ->
                val expected = if (target in withAForm) listOf(FIX, REVERT) else listOf(REVERT)
                assertEquals(expected, actionsOf(target, SyncIssueKind.UPDATE_REFUSED, reason), "$target, $reason")
            }
        }
    }

    @Test
    fun everything_the_server_refused_has_at_least_one_action() {
        everyTarget.forEach { target ->
            SyncIssueKind.entries.filter { it != SyncIssueKind.BLOCKED_BY_PARENT }.forEach { kind ->
                (RefusalReason.entries + null).forEach { reason ->
                    assertTrue(actionsOf(target, kind, reason).isNotEmpty(), "$target, $kind, $reason")
                }
            }
        }
    }

    @Test
    fun the_server_value_is_known_on_this_device_only_for_a_consumption_line_that_kept_it() {
        val refusedChange = SyncIssue(SyncIssueKind.UPDATE_REFUSED, RefusalReason.INSUFFICIENT_STOCK)
        assertTrue(issueItem(SyncIssueTarget.CONSUMPTION_LINE, "cl", refusedChange, quantity = 50.0, serverQuantity = 2.0).serverValueKnownLocally)
        assertFalse(issueItem(SyncIssueTarget.CONSUMPTION_LINE, "cl", refusedChange, quantity = 50.0, serverQuantity = null).serverValueKnownLocally)
        assertFalse(
            issueItem(SyncIssueTarget.PURCHASE_LINE, "pl", refusedChange, quantity = 3.0, serverQuantity = 10.0).serverValueKnownLocally,
            "the server price and supplier of a purchase line are not kept on the device",
        )
        assertFalse(issueItem(SyncIssueTarget.CONSUMPTION_LINE, "cl", SyncIssue(SyncIssueKind.REFUSED, RefusalReason.INSUFFICIENT_STOCK), serverQuantity = 2.0).serverValueKnownLocally)
        (everyTarget - SyncIssueTarget.CONSUMPTION_LINE).forEach { assertFalse(issueItem(it, "x", refusedChange, serverQuantity = 1.0).serverValueKnownLocally, "$it") }
    }
}
