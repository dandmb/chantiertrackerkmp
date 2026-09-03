package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.dto.CreateProjectRequestDto
import com.dmb.chantiertracker.data.remote.dto.MemberDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDetailDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import com.dmb.chantiertracker.data.remote.dto.UpdateProjectRequestDto

object SyncError {
    const val PLAN_LIMIT = "PLAN_LIMIT"
    const val REJECTED = "REJECTED"
}

private const val FALLBACK_CURRENCY = "USD"
private const val FALLBACK_TIMEZONE = "UTC"

fun ProjectDto.toSyncedEntity(localId: String, syncedAt: Long, previous: ProjectEntity? = null): ProjectEntity {
    val remoteMillis = parseServerTimestampMillis(updatedAt)
    return ProjectEntity(
        localId = localId,
        serverId = id,
        name = name,
        description = description,
        location = location,
        currency = currency ?: previous?.currency ?: FALLBACK_CURRENCY,
        timezone = timezone ?: previous?.timezone ?: FALLBACK_TIMEZONE,
        status = status,
        ownerId = ownerId ?: previous?.ownerId,
        createdAt = createdAt ?: previous?.createdAt,
        syncStatus = SyncStatus.SYNCED,
        pendingOp = PendingOp.NONE,
        locallyModifiedAt = remoteMillis ?: syncedAt,
        lastSyncedAt = syncedAt,
        remoteUpdatedAt = remoteMillis,
        lastSyncError = null,
    )
}

fun ProjectDetailDto.toSyncedEntity(localId: String, syncedAt: Long, previous: ProjectEntity? = null): ProjectEntity {
    val remoteMillis = parseServerTimestampMillis(updatedAt)
    return ProjectEntity(
        localId = localId,
        serverId = id,
        name = name,
        description = description,
        location = location,
        currency = currency,
        timezone = timezone,
        status = status,
        ownerId = ownerId,
        createdAt = createdAt ?: previous?.createdAt,
        syncStatus = SyncStatus.SYNCED,
        pendingOp = PendingOp.NONE,
        locallyModifiedAt = remoteMillis ?: syncedAt,
        lastSyncedAt = syncedAt,
        remoteUpdatedAt = remoteMillis,
        lastSyncError = null,
    )
}

fun ProjectEntity.toCreateRequest() = CreateProjectRequestDto(
    name = name,
    description = description?.ifBlank { null },
    location = location?.ifBlank { null },
    currency = currency.ifBlank { null },
    timezone = timezone,
)

fun ProjectEntity.toUpdateRequest() = UpdateProjectRequestDto(
    name = name,
    description = description,
    location = location,
    currency = currency,
    timezone = timezone,
    status = status,
)

fun MemberDto.toEntity(projectLocalId: String) = ProjectMemberEntity(
    projectLocalId = projectLocalId,
    userId = userId,
    name = name,
    email = email,
    role = role,
)
