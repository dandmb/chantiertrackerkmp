package com.dmb.chantiertracker.presentation.sync

import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueParent
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.canBeRetried
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.presentation.format.formatAmount
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.sync_hint_entry_date_restricted
import com.dmb.chantiertracker.resources.sync_hint_insufficient_role
import com.dmb.chantiertracker.resources.sync_hint_plan_limit
import com.dmb.chantiertracker.resources.sync_hint_project_or_stage_inactive
import com.dmb.chantiertracker.resources.sync_item_attachment
import com.dmb.chantiertracker.resources.sync_item_consumption_line
import com.dmb.chantiertracker.resources.sync_item_entry
import com.dmb.chantiertracker.resources.sync_item_entry_purchase
import com.dmb.chantiertracker.resources.sync_item_entry_work
import com.dmb.chantiertracker.resources.sync_item_material
import com.dmb.chantiertracker.resources.sync_item_project
import com.dmb.chantiertracker.resources.sync_item_purchase_line
import com.dmb.chantiertracker.resources.sync_item_stage
import com.dmb.chantiertracker.resources.sync_reason_blocked
import com.dmb.chantiertracker.resources.sync_reason_delete_refused
import com.dmb.chantiertracker.resources.sync_reason_deleted_on_server
import com.dmb.chantiertracker.resources.sync_reason_duplicate_entry
import com.dmb.chantiertracker.resources.sync_reason_duplicate_material
import com.dmb.chantiertracker.resources.sync_reason_entry_date_restricted
import com.dmb.chantiertracker.resources.sync_reason_file_refused
import com.dmb.chantiertracker.resources.sync_reason_insufficient_role
import com.dmb.chantiertracker.resources.sync_reason_insufficient_stock
import com.dmb.chantiertracker.resources.sync_reason_invalid_value
import com.dmb.chantiertracker.resources.sync_reason_plan_limit
import com.dmb.chantiertracker.resources.sync_reason_project_or_stage_inactive
import com.dmb.chantiertracker.resources.sync_reason_stock_consumed
import com.dmb.chantiertracker.resources.sync_reason_unknown
import com.dmb.chantiertracker.resources.sync_retry_accepted
import com.dmb.chantiertracker.resources.sync_retry_not_sent
import com.dmb.chantiertracker.resources.sync_retry_still_refused
import com.dmb.chantiertracker.resources.sync_status_blocked
import com.dmb.chantiertracker.resources.sync_status_delete_refused
import com.dmb.chantiertracker.resources.sync_status_deleted_on_server
import com.dmb.chantiertracker.resources.sync_status_refused
import com.dmb.chantiertracker.resources.sync_status_update_refused
import com.dmb.chantiertracker.resources.sync_typed_versus_server
import com.dmb.chantiertracker.resources.sync_waiting_on_entry
import com.dmb.chantiertracker.resources.sync_waiting_on_material
import com.dmb.chantiertracker.resources.sync_waiting_on_material_named
import com.dmb.chantiertracker.resources.sync_waiting_on_project
import com.dmb.chantiertracker.resources.sync_waiting_on_project_named
import com.dmb.chantiertracker.resources.sync_waiting_on_purchase_entry
import com.dmb.chantiertracker.resources.sync_waiting_on_stage
import com.dmb.chantiertracker.resources.sync_waiting_on_stage_named
import com.dmb.chantiertracker.resources.sync_waiting_on_work_entry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun RefusalReason?.sentenceRes(): StringResource = when (this) {
    RefusalReason.PLAN_LIMIT -> Res.string.sync_reason_plan_limit
    RefusalReason.PROJECT_OR_STAGE_INACTIVE -> Res.string.sync_reason_project_or_stage_inactive
    RefusalReason.ENTRY_DATE_RESTRICTED -> Res.string.sync_reason_entry_date_restricted
    RefusalReason.INSUFFICIENT_ROLE -> Res.string.sync_reason_insufficient_role
    RefusalReason.INSUFFICIENT_STOCK -> Res.string.sync_reason_insufficient_stock
    RefusalReason.STOCK_CONSUMED -> Res.string.sync_reason_stock_consumed
    RefusalReason.DUPLICATE_ENTRY -> Res.string.sync_reason_duplicate_entry
    RefusalReason.DUPLICATE_MATERIAL -> Res.string.sync_reason_duplicate_material
    RefusalReason.INVALID_VALUE -> Res.string.sync_reason_invalid_value
    RefusalReason.FILE_REFUSED -> Res.string.sync_reason_file_refused
    RefusalReason.UNKNOWN, null -> Res.string.sync_reason_unknown
}

fun RefusalReason?.instructionRes(): StringResource? = when (this) {
    RefusalReason.PLAN_LIMIT -> Res.string.sync_hint_plan_limit
    RefusalReason.PROJECT_OR_STAGE_INACTIVE -> Res.string.sync_hint_project_or_stage_inactive
    RefusalReason.ENTRY_DATE_RESTRICTED -> Res.string.sync_hint_entry_date_restricted
    RefusalReason.INSUFFICIENT_ROLE -> Res.string.sync_hint_insufficient_role
    else -> null
}

fun SyncIssueKind.statusRes(): StringResource = when (this) {
    SyncIssueKind.REFUSED -> Res.string.sync_status_refused
    SyncIssueKind.UPDATE_REFUSED -> Res.string.sync_status_update_refused
    SyncIssueKind.DELETE_REFUSED -> Res.string.sync_status_delete_refused
    SyncIssueKind.DELETED_ON_SERVER -> Res.string.sync_status_deleted_on_server
    SyncIssueKind.BLOCKED_BY_PARENT -> Res.string.sync_status_blocked
}

fun RetryOutcome.noticeRes(): StringResource = when (this) {
    RetryOutcome.ACCEPTED -> Res.string.sync_retry_accepted
    RetryOutcome.STILL_REFUSED -> Res.string.sync_retry_still_refused
    RetryOutcome.NOT_SENT -> Res.string.sync_retry_not_sent
}

@Composable
fun SyncIssueItem.sentence(): String =
    if (issue.kind == SyncIssueKind.BLOCKED_BY_PARENT) blockedBy.waitingSentence() else issue.sentence()

@Composable
private fun SyncIssueParent?.waitingSentence(): String {
    val name = this?.name?.trim().orEmpty()
    return when (this?.target) {
        SyncIssueTarget.PROJECT ->
            if (name.isEmpty()) stringResource(Res.string.sync_waiting_on_project) else stringResource(Res.string.sync_waiting_on_project_named, name)
        SyncIssueTarget.STAGE ->
            if (name.isEmpty()) stringResource(Res.string.sync_waiting_on_stage) else stringResource(Res.string.sync_waiting_on_stage_named, name)
        SyncIssueTarget.MATERIAL ->
            if (name.isEmpty()) stringResource(Res.string.sync_waiting_on_material) else stringResource(Res.string.sync_waiting_on_material_named, name)
        SyncIssueTarget.ENTRY -> {
            val day = date?.trim().orEmpty()
            when {
                day.isEmpty() -> stringResource(Res.string.sync_waiting_on_entry)
                entryType == EntryType.PURCHASE -> stringResource(Res.string.sync_waiting_on_purchase_entry, formatIsoDate(day))
                entryType == EntryType.WORK -> stringResource(Res.string.sync_waiting_on_work_entry, formatIsoDate(day))
                else -> stringResource(Res.string.sync_waiting_on_entry)
            }
        }
        else -> stringResource(Res.string.sync_reason_blocked)
    }
}

@Composable
fun SyncIssue.sentence(): String = when (kind) {
    SyncIssueKind.BLOCKED_BY_PARENT -> stringResource(Res.string.sync_reason_blocked)
    SyncIssueKind.DELETED_ON_SERVER -> stringResource(Res.string.sync_reason_deleted_on_server)
    SyncIssueKind.DELETE_REFUSED -> stringResource(Res.string.sync_reason_delete_refused, stringResource(reason.sentenceRes()))
    SyncIssueKind.REFUSED, SyncIssueKind.UPDATE_REFUSED -> stringResource(reason.sentenceRes())
}

@Composable
fun SyncIssue.instruction(): String? = if (canBeRetried) reason.instructionRes()?.let { stringResource(it) } else null

@Composable
fun SyncIssueItem.title(): String = when (target) {
    SyncIssueTarget.PROJECT -> stringResource(Res.string.sync_item_project)
    SyncIssueTarget.STAGE -> stringResource(Res.string.sync_item_stage)
    SyncIssueTarget.MATERIAL -> stringResource(Res.string.sync_item_material, label.orEmpty())
    SyncIssueTarget.ENTRY -> stringResource(
        when (entryType) {
            EntryType.PURCHASE -> Res.string.sync_item_entry_purchase
            EntryType.WORK -> Res.string.sync_item_entry_work
            EntryType.UNKNOWN, null -> Res.string.sync_item_entry
        },
    )
    SyncIssueTarget.PURCHASE_LINE ->
        stringResource(Res.string.sync_item_purchase_line, label.orEmpty(), quantity?.let { formatAmount(it) }.orEmpty(), unit.orEmpty())
    SyncIssueTarget.CONSUMPTION_LINE ->
        stringResource(Res.string.sync_item_consumption_line, label.orEmpty(), quantity?.let { formatAmount(it) }.orEmpty(), unit.orEmpty())
    SyncIssueTarget.ATTACHMENT -> stringResource(Res.string.sync_item_attachment, label.orEmpty())
}

@Composable
fun SyncIssueItem.typedVersusServer(): String? {
    val typed = quantity
    val kept = serverQuantity
    if (issue.kind != SyncIssueKind.UPDATE_REFUSED || typed == null || kept == null) return null
    val shownUnit = unit?.trim().orEmpty()
    val withUnit = { amount: Double -> if (shownUnit.isEmpty()) formatAmount(amount) else formatAmount(amount) + " " + shownUnit }
    return stringResource(Res.string.sync_typed_versus_server, withUnit(typed), withUnit(kept))
}
