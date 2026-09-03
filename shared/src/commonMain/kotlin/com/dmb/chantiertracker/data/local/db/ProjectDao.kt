package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE pendingOp != 'DELETE'")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE localId = :localId")
    fun observeProject(localId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE syncStatus != 'SYNCED'")
    suspend fun findPending(): List<ProjectEntity>

    @Query("SELECT * FROM projects")
    suspend fun findAll(): List<ProjectEntity>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Upsert
    suspend fun upsertAll(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("SELECT * FROM project_members WHERE projectLocalId = :projectLocalId")
    fun observeMembers(projectLocalId: String): Flow<List<ProjectMemberEntity>>

    @Upsert
    suspend fun upsertMembers(members: List<ProjectMemberEntity>)

    @Query("DELETE FROM project_members WHERE projectLocalId = :projectLocalId")
    suspend fun clearMembers(projectLocalId: String)
}
