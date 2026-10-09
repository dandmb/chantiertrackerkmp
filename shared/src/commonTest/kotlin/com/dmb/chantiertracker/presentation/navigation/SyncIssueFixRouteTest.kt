package com.dmb.chantiertracker.presentation.navigation

import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.support.issueItem
import com.dmb.chantiertracker.support.refusedIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncIssueFixRouteTest {

    private val invalid = refusedIssue(RefusalReason.INVALID_VALUE)

    @Test
    fun fixing_reopens_the_form_of_the_refused_element() {
        assertEquals(EditProjectRoute("p1"), issueItem(SyncIssueTarget.PROJECT, "p1", invalid, projectLocalId = "p1").fixRoute())
        assertEquals(StageDetailRoute("st1"), issueItem(SyncIssueTarget.STAGE, "st1", invalid, stageLocalId = "st1").fixRoute())
        assertEquals(EntrySummaryRoute("e1"), issueItem(SyncIssueTarget.ENTRY, "e1", invalid, entryLocalId = "e1").fixRoute())
        assertEquals(
            PurchaseLineFormRoute(entryLocalId = "e1", projectLocalId = "p1", lineLocalId = "pl1", currency = "EUR"),
            issueItem(SyncIssueTarget.PURCHASE_LINE, "pl1", refusedIssue(RefusalReason.STOCK_CONSUMED, kind = SyncIssueKind.UPDATE_REFUSED), entryLocalId = "e1", currency = "EUR").fixRoute(),
        )
        assertEquals(
            ConsumptionLineFormRoute(entryLocalId = "e2", projectLocalId = "p1", lineLocalId = "cl1"),
            issueItem(SyncIssueTarget.CONSUMPTION_LINE, "cl1", refusedIssue(RefusalReason.INSUFFICIENT_STOCK), entryLocalId = "e2").fixRoute(),
        )
    }

    @Test
    fun what_has_no_form_or_cannot_be_fixed_leads_nowhere() {
        assertNull(issueItem(SyncIssueTarget.MATERIAL, "m1", refusedIssue(RefusalReason.DUPLICATE_MATERIAL)).fixRoute())
        assertNull(issueItem(SyncIssueTarget.ATTACHMENT, "a1", refusedIssue(RefusalReason.FILE_REFUSED)).fixRoute())
        assertNull(issueItem(SyncIssueTarget.PURCHASE_LINE, "pl1", invalid, entryLocalId = null).fixRoute(), "a line whose entry is unknown cannot open its form")
        assertNull(issueItem(SyncIssueTarget.PROJECT, "p1", refusedIssue(RefusalReason.PLAN_LIMIT)).fixRoute(), "editing a project does not lift a plan limit")
    }
}
