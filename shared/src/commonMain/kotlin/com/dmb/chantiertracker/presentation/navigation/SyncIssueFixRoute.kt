package com.dmb.chantiertracker.presentation.navigation

import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.canBeFixed

fun SyncIssueItem.fixRoute(): Any? {
    if (!canBeFixed) return null
    return when (target) {
        SyncIssueTarget.PROJECT -> EditProjectRoute(projectLocalId)
        SyncIssueTarget.STAGE -> StageDetailRoute(stageLocalId ?: localId)
        SyncIssueTarget.ENTRY -> EntrySummaryRoute(localId)
        SyncIssueTarget.PURCHASE_LINE -> entryLocalId?.let { PurchaseLineFormRoute(it, projectLocalId, localId, currency) }
        SyncIssueTarget.CONSUMPTION_LINE -> entryLocalId?.let { ConsumptionLineFormRoute(it, projectLocalId, localId) }
        SyncIssueTarget.MATERIAL, SyncIssueTarget.ATTACHMENT -> null
    }
}
