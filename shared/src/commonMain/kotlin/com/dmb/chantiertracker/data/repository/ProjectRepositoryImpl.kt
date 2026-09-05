@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val DEFAULT_CURRENCY = "USD"

class ProjectRepositoryImpl(
    private val dao: ProjectDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> =
        dao.observeProjects().map { rows -> rows.map(ProjectEntity::toProject) }

    override fun observeProject(localId: String): Flow<ProjectDetail?> =
        dao.observeProject(localId).map { it?.toProjectDetail() }

    override fun observeMembers(localId: String): Flow<List<ProjectMember>> =
        dao.observeMembers(localId).map { rows -> rows.map(ProjectMemberEntity::toMember) }

    override fun observeActiveProjectCount(ownerId: Long): Flow<Int> =
        dao.observeActiveOwnedCount(ownerId)

    override suspend fun createProject(input: CreateProjectInput): String {
        val localId = newLocalId()
        val now = clock.nowEpochMillis()
        dao.upsert(
            ProjectEntity(
                localId = localId,
                serverId = null,
                name = input.name,
                description = input.description?.ifBlank { null },
                location = input.location?.ifBlank { null },
                currency = input.currency?.ifBlank { null } ?: DEFAULT_CURRENCY,
                timezone = input.timezone,
                status = ProjectStatus.IN_PROGRESS.name,
                ownerId = null,
                createdAt = null,
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.CREATE,
                locallyModifiedAt = now,
                lastSyncedAt = null,
                remoteUpdatedAt = null,
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
        return localId
    }

    override suspend fun updateProject(localId: String, input: UpdateProjectInput) {
        val existing = dao.findByLocalId(localId) ?: return
        dao.upsert(
            existing.copy(
                name = input.name,
                description = input.description?.ifBlank { null },
                location = input.location?.ifBlank { null },
                currency = input.currency.ifBlank { existing.currency },
                timezone = input.timezone,
                status = input.status.name,
                syncStatus = SyncStatus.PENDING,
                pendingOp = if (existing.pendingOp == PendingOp.CREATE) PendingOp.CREATE else PendingOp.UPDATE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }

    override suspend fun deleteProject(localId: String) {
        val existing = dao.findByLocalId(localId) ?: return
        if (existing.serverId == null) {
            dao.deleteByLocalId(localId)
            return
        }
        dao.upsert(
            existing.copy(
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.DELETE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }

    override suspend fun refresh() {
        syncer.syncNow()
    }

    override suspend fun refreshProject(localId: String) {
        syncer.syncProject(localId)
    }
}

internal fun String.toProjectStatus(): ProjectStatus = when (uppercase()) {
    "IN_PROGRESS" -> ProjectStatus.IN_PROGRESS
    "SUSPENDED" -> ProjectStatus.SUSPENDED
    "COMPLETED" -> ProjectStatus.COMPLETED
    else -> ProjectStatus.UNKNOWN
}

internal fun ProjectEntity.toProject(): Project = Project(
    localId = localId,
    name = name,
    description = description,
    location = location,
    status = status.toProjectStatus(),
    createdAt = createdAt,
)

internal fun ProjectEntity.toProjectDetail(): ProjectDetail = ProjectDetail(
    localId = localId,
    name = name,
    description = description,
    location = location,
    currency = currency,
    timezone = timezone,
    status = status.toProjectStatus(),
    ownerId = ownerId,
)

internal fun ProjectMemberEntity.toMember(): ProjectMember = ProjectMember(
    userId = userId,
    name = name,
    email = email,
    role = when (role.uppercase()) {
        "ADMIN" -> ProjectRole.ADMIN
        "SUPERVISOR" -> ProjectRole.SUPERVISOR
        else -> ProjectRole.UNKNOWN
    },
)
