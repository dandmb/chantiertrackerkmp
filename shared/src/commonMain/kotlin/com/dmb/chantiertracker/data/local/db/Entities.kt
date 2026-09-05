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

@Entity(tableName = "plan_usage")
data class PlanUsageEntity(
    @PrimaryKey val id: Int = 0,
    val plan: String,
    val projectsLimit: Int?,
    val refreshedAt: Long,
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

// A daily log is never created/updated/deleted through its own endpoint — the
// backend creates it as a side effect of the first entry posted for a
// (stage, date) pair ("Les POST créent la journée si elle n'existe pas").
// Mirrored here: no pendingOp of its own, just a serverId learned once one of
// its entries has synced (see DailyEntryResponse.dailyLogId).
@Entity(
    tableName = "daily_logs",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["localId"],
            childColumns = ["stageLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stageLocalId"), Index(value = ["stageLocalId", "date"], unique = true)],
)
data class DailyLogEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val stageLocalId: String,
    val date: String,
    val locallyCreatedAt: Long,
    val lastSyncedAt: Long?,
)

@Entity(
    tableName = "daily_entries",
    foreignKeys = [
        ForeignKey(
            entity = DailyLogEntity::class,
            parentColumns = ["localId"],
            childColumns = ["dailyLogLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dailyLogLocalId"), Index(value = ["dailyLogLocalId", "type"], unique = true)],
)
data class DailyEntryEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val dailyLogLocalId: String,
    val type: String,
    val summary: String?,
    val createdById: Long?,
    val createdAt: String?,
    val modifiedById: Long?,
    val modifiedAt: String?,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastSyncError: String?,
)

// Referential, scoped to the project (not the stage) — "le ciment restant des
// fondations sert forcément à l'élévation". Never deleted by the backend
// (PATCH only, to rename/change the unit), so pendingOp never reaches DELETE
// here in practice, even though the column is the shared enum.
@Entity(
    tableName = "materials",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["localId"],
            childColumns = ["projectLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectLocalId"), Index(value = ["projectLocalId", "name"], unique = true)],
)
data class MaterialEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val projectLocalId: String,
    val name: String,
    val unit: String,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastSyncError: String?,
)

@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = DailyEntryEntity::class,
            parentColumns = ["localId"],
            childColumns = ["entryLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["localId"],
            childColumns = ["materialLocalId"],
        ),
    ],
    indices = [Index("entryLocalId"), Index("materialLocalId")],
)
data class PurchaseLineEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val entryLocalId: String,
    val materialLocalId: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val supplier: String?,
    val createdAt: String?,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastSyncError: String?,
)

@Entity(
    tableName = "consumption_lines",
    foreignKeys = [
        ForeignKey(
            entity = DailyEntryEntity::class,
            parentColumns = ["localId"],
            childColumns = ["entryLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["localId"],
            childColumns = ["materialLocalId"],
        ),
    ],
    indices = [Index("entryLocalId"), Index("materialLocalId")],
)
data class ConsumptionLineEntity(
    @PrimaryKey val localId: String,
    val serverId: Long?,
    val entryLocalId: String,
    val materialLocalId: String,
    val quantity: Double,
    val createdAt: String?,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastSyncError: String?,
)
