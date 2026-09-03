package com.dmb.chantiertracker.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DB_FILE_NAME = "chantiertracker.db"

enum class SyncStatus { SYNCED, PENDING, CONFLICTED }

enum class PendingOp { NONE, CREATE, UPDATE, DELETE }

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String,
    val timezone: String,
    val status: String,
    val ownerId: Long?,
    val createdAt: String?,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastSyncError: String?,
)

@Entity(tableName = "project_members", primaryKeys = ["projectLocalId", "userId"])
data class ProjectMemberEntity(
    val projectLocalId: String,
    val userId: Long,
    val name: String,
    val email: String,
    val role: String,
)

@Entity(
    tableName = "stages",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["localId"],
            childColumns = ["projectLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectLocalId")],
)
data class StageEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val projectLocalId: String,
    val name: String,
    val description: String?,
    val estimatedBudget: Double?,
    val startDate: String?,
    val endDate: String?,
    val status: String,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastSyncError: String?,
)
