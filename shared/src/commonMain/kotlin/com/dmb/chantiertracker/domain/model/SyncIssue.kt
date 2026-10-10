package com.dmb.chantiertracker.domain.model

enum class SyncIssueKind { REFUSED, UPDATE_REFUSED, DELETE_REFUSED, DELETED_ON_SERVER, BLOCKED_BY_PARENT }

enum class RefusalReason {
    PLAN_LIMIT,
    PROJECT_OR_STAGE_INACTIVE,
    ENTRY_DATE_RESTRICTED,
    INSUFFICIENT_ROLE,
    INSUFFICIENT_STOCK,
    STOCK_CONSUMED,
    DUPLICATE_ENTRY,
    DUPLICATE_MATERIAL,
    INVALID_VALUE,
    FILE_REFUSED,
    UNKNOWN,
}

data class SyncIssue(
    val kind: SyncIssueKind,
    val reason: RefusalReason? = null,
    val serverCode: String? = null,
)

val RefusalReason.dependsOnSomethingElse: Boolean
    get() = this == RefusalReason.PLAN_LIMIT ||
        this == RefusalReason.PROJECT_OR_STAGE_INACTIVE ||
        this == RefusalReason.INSUFFICIENT_ROLE

val RefusalReason.creationIsSentAgainByEachPass: Boolean
    get() = when (this) {
        RefusalReason.PLAN_LIMIT,
        RefusalReason.PROJECT_OR_STAGE_INACTIVE,
        RefusalReason.INSUFFICIENT_ROLE,
        RefusalReason.INSUFFICIENT_STOCK,
        -> true
        RefusalReason.INVALID_VALUE,
        RefusalReason.DUPLICATE_ENTRY,
        RefusalReason.DUPLICATE_MATERIAL,
        RefusalReason.ENTRY_DATE_RESTRICTED,
        RefusalReason.STOCK_CONSUMED,
        RefusalReason.FILE_REFUSED,
        RefusalReason.UNKNOWN,
        -> false
    }

val SyncIssue.waitsForACorrection: Boolean
    get() = kind == SyncIssueKind.REFUSED && !(reason ?: RefusalReason.UNKNOWN).creationIsSentAgainByEachPass

val SyncIssue.canBeRetried: Boolean
    get() = (kind == SyncIssueKind.REFUSED || kind == SyncIssueKind.UPDATE_REFUSED) && reason?.dependsOnSomethingElse == true

val SyncIssue.countsToReview: Boolean
    get() = kind != SyncIssueKind.BLOCKED_BY_PARENT

enum class SyncIssueTarget { PROJECT, STAGE, MATERIAL, ENTRY, PURCHASE_LINE, CONSUMPTION_LINE, ATTACHMENT }

data class SyncIssueParent(
    val target: SyncIssueTarget,
    val name: String? = null,
    val entryType: EntryType? = null,
    val date: String? = null,
)

data class SyncIssueItem(
    val target: SyncIssueTarget,
    val localId: String,
    val issue: SyncIssue,
    val projectLocalId: String,
    val projectName: String,
    val stageLocalId: String? = null,
    val stageName: String? = null,
    val dailyLogLocalId: String? = null,
    val date: String? = null,
    val entryType: EntryType? = null,
    val label: String? = null,
    val unit: String? = null,
    val quantity: Double? = null,
    val serverQuantity: Double? = null,
    val blockedBy: SyncIssueParent? = null,
    val entryLocalId: String? = null,
    val currency: String? = null,
) {
    val key: String get() = "$target:$localId"
}

enum class SyncIssueAction { FIX, RETRY, REVERT, DISCARD, ACKNOWLEDGE }

val SyncIssueTarget.hasAnEditForm: Boolean
    get() = this == SyncIssueTarget.PROJECT ||
        this == SyncIssueTarget.STAGE ||
        this == SyncIssueTarget.ENTRY ||
        this == SyncIssueTarget.PURCHASE_LINE ||
        this == SyncIssueTarget.CONSUMPTION_LINE

val RefusalReason?.canBeLiftedByACorrection: Boolean
    get() = this == RefusalReason.INSUFFICIENT_STOCK ||
        this == RefusalReason.STOCK_CONSUMED ||
        this == RefusalReason.INVALID_VALUE ||
        this == RefusalReason.UNKNOWN ||
        this == null

val SyncIssueItem.canBeFixed: Boolean
    get() = target.hasAnEditForm && issue.reason.canBeLiftedByACorrection

val SyncIssueItem.actions: List<SyncIssueAction>
    get() = when (issue.kind) {
        SyncIssueKind.BLOCKED_BY_PARENT -> emptyList()
        SyncIssueKind.DELETED_ON_SERVER, SyncIssueKind.DELETE_REFUSED -> listOf(SyncIssueAction.ACKNOWLEDGE)
        SyncIssueKind.UPDATE_REFUSED -> buildList {
            if (canBeFixed) add(SyncIssueAction.FIX)
            if (issue.canBeRetried) add(SyncIssueAction.RETRY)
            add(SyncIssueAction.REVERT)
        }
        SyncIssueKind.REFUSED -> buildList {
            if (canBeFixed) add(SyncIssueAction.FIX)
            if (issue.canBeRetried) add(SyncIssueAction.RETRY)
            add(SyncIssueAction.DISCARD)
        }
    }

val SyncIssueItem.removesLocalDataWhenAcknowledged: Boolean
    get() = issue.kind != SyncIssueKind.DELETE_REFUSED

val SyncIssueItem.serverValueKnownLocally: Boolean
    get() = issue.kind == SyncIssueKind.UPDATE_REFUSED && target == SyncIssueTarget.CONSUMPTION_LINE && serverQuantity != null

val knownRefusalCodes: Map<String, RefusalReason> = mapOf(
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
    "MATERIAL_PROJECT_MISMATCH" to RefusalReason.INVALID_VALUE,
    "ENTRY_TYPE_MISMATCH" to RefusalReason.INVALID_VALUE,
    "ATTACHMENT_TOO_LARGE" to RefusalReason.FILE_REFUSED,
    "INVALID_ATTACHMENT_TYPE" to RefusalReason.FILE_REFUSED,
    "INVALID_ATTACHMENT_NAME" to RefusalReason.FILE_REFUSED,
    "UNSUPPORTED_IMAGE" to RefusalReason.FILE_REFUSED,
    "UNREADABLE_VIDEO" to RefusalReason.FILE_REFUSED,
    "VIDEO_TOO_LONG" to RefusalReason.FILE_REFUSED,
    "LOCAL_FILE_MISSING" to RefusalReason.FILE_REFUSED,
)

fun refusalReasonOf(serverCode: String?): RefusalReason = knownRefusalCodes[serverCode] ?: RefusalReason.UNKNOWN

data class UnsentWrites(
    val projects: Int = 0,
    val stages: Int = 0,
    val materials: Int = 0,
    val entries: Int = 0,
    val lines: Int = 0,
    val attachments: Int = 0,
) {
    val total: Int get() = projects + stages + materials + entries + lines + attachments
}
