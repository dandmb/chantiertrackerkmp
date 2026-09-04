package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ProjectDao] — lets sync tests run on every platform without Room. */
class FakeProjectDao(initial: List<ProjectEntity> = emptyList()) : ProjectDao {

    private val projects = MutableStateFlow(initial.associateBy { it.localId })
    private val members = MutableStateFlow<List<ProjectMemberEntity>>(emptyList())

    val stored: List<ProjectEntity> get() = projects.value.values.toList()

    override fun observeProjects(): Flow<List<ProjectEntity>> =
        projects.map { rows -> rows.values.filter { it.pendingOp != PendingOp.DELETE } }

    override fun observeProject(localId: String): Flow<ProjectEntity?> =
        projects.map { it[localId] }

    override suspend fun findByLocalId(localId: String): ProjectEntity? = projects.value[localId]

    override suspend fun findByServerId(serverId: Long): ProjectEntity? =
        projects.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<ProjectEntity> =
        projects.value.values.filter { it.syncStatus != SyncStatus.SYNCED }

    override fun observeActiveOwnedCount(ownerId: Long): Flow<Int> =
        projects.map { rows ->
            rows.values.count {
                it.status == "IN_PROGRESS" &&
                    it.pendingOp != PendingOp.DELETE &&
                    (it.ownerId == ownerId || it.ownerId == null)
            }
        }

    override suspend fun findAll(): List<ProjectEntity> = projects.value.values.toList()

    override suspend fun upsert(project: ProjectEntity) {
        projects.value = projects.value + (project.localId to project)
    }

    override suspend fun upsertAll(projects: List<ProjectEntity>) {
        this.projects.value = this.projects.value + projects.associateBy { it.localId }
    }

    override suspend fun deleteByLocalId(localId: String) {
        projects.value = projects.value - localId
    }

    override fun observeMembers(projectLocalId: String): Flow<List<ProjectMemberEntity>> =
        members.map { list -> list.filter { it.projectLocalId == projectLocalId } }

    override suspend fun upsertMembers(members: List<ProjectMemberEntity>) {
        val keys = members.map { it.projectLocalId to it.userId }.toSet()
        this.members.value = this.members.value.filterNot { (it.projectLocalId to it.userId) in keys } + members
    }

    override suspend fun clearMembers(projectLocalId: String) {
        members.value = members.value.filterNot { it.projectLocalId == projectLocalId }
    }
}
