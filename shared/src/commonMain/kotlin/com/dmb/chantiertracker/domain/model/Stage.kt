package com.dmb.chantiertracker.domain.model

data class Stage(
    val localId: String,
    val projectLocalId: String,
    val name: String,
    val estimatedBudget: Double?,
    val status: StageStatus,
)

enum class StageStatus { IN_PROGRESS, COMPLETED, UNKNOWN }

data class StageDetail(
    val localId: String,
    val projectLocalId: String,
    val name: String,
    val description: String?,
    val estimatedBudget: Double?,
    val startDate: String?,
    val endDate: String?,
    val status: StageStatus,
)

data class CreateStageInput(
    val projectLocalId: String,
    val name: String,
    val description: String?,
    val estimatedBudget: Double?,
    val startDate: String?,
    val endDate: String?,
)

data class UpdateStageInput(
    val name: String,
    val description: String?,
    val estimatedBudget: Double?,
    val startDate: String?,
    val endDate: String?,
    val status: StageStatus,
)

/** Owner or an ADMIN project member — may administer the project (edit it, set stage budgets). */
fun projectAdmin(ownerId: Long?, members: List<ProjectMember>, currentUserId: Long?): Boolean {
    if (currentUserId == null) return false
    if (ownerId == currentUserId) return true
    return members.any { it.userId == currentUserId && it.role == ProjectRole.ADMIN }
}
