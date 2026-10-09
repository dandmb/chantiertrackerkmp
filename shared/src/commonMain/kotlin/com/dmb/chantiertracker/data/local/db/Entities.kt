package com.dmb.chantiertracker.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DB_FILE_NAME = "chantiertracker.db"

enum class SyncStatus { SYNCED, PENDING, CONFLICTED }

enum class PendingOp { NONE, CREATE, UPDATE, DELETE }

interface SyncedRow {
    val serverId: Long?
    val syncStatus: SyncStatus
    val pendingOp: PendingOp
    val lastSyncError: String?
    val serverErrorCode: String?
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val localId: String,
    override val serverId: Long?,
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String,
    val timezone: String,
    val status: String,
    val ownerId: Long?,
    val createdAt: String?,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
    // The project OWNER's plan (never the caller's), from ProjectDetailDto.
    // Null until the first detail pull — the supervisor-limit pre-check
    // fails open in that window (ADR-33). Not set by the project-list pull.
    val ownerPlan: String? = null,
    // The owner's entitlements as already combined by the backend (plan +
    // founder status), stored as one block. ownerIsFounder null = never
    // received; ownerMaxHistoryDays / ownerMaxSupervisorsPerProject null only
    // mean "unlimited" once the block is there.
    val ownerIsFounder: Boolean? = null,
    val ownerCanExportPdf: Boolean? = null,
    val ownerMaxHistoryDays: Int? = null,
    val ownerMaxVideos: Int? = null,
    val ownerMaxVideoDurationSeconds: Int? = null,
    val ownerMaxSupervisorsPerProject: Int? = null,
) : SyncedRow

@Entity(tableName = "project_members", primaryKeys = ["projectLocalId", "userId"])
data class ProjectMemberEntity(
    val projectLocalId: String,
    val userId: Long,
    val name: String,
    val email: String,
    val role: String,
)

// Read-through cache of a project's invitations (server id as PK — never
// created locally, see ADR-32). Replaced wholesale on each pull, like
// project_members.
@Entity(
    tableName = "invitations",
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
data class InvitationEntity(
    @PrimaryKey val id: Long,
    val projectLocalId: String,
    val email: String,
    val role: String,
    val invitedById: Long?,
    val createdAt: String?,
    val expiresAt: String?,
    val status: String,
)

// projectsUsed/photosUsed/photosLimit/videosUsed/videosLimit/
// videoDurationLimitSeconds/supervisorsUsed/supervisorsLimit/planExpiresAt/
// hasStripeCustomer added ADR-49 (billing screen) — all nullable so a row
// cached before this migration (MIGRATION_10_11) reads back as "unknown"
// rather than a fabricated zero, until the next refreshPlanUsage() call.
@Entity(tableName = "plan_usage")
data class PlanUsageEntity(
    @PrimaryKey val id: Int = 0,
    val plan: String,
    val projectsLimit: Int?,
    val refreshedAt: Long,
    val projectsUsed: Int? = null,
    val photosUsed: Int? = null,
    val photosLimit: Int? = null,
    val videosUsed: Int? = null,
    val videosLimit: Int? = null,
    val videoDurationLimitSeconds: Int? = null,
    val supervisorsUsed: Int? = null,
    val supervisorsLimit: Int? = null,
    val planExpiresAt: String? = null,
    val hasStripeCustomer: Boolean? = null,
    val isFounder: Boolean? = null,
    // historyDaysLimit null means "unlimited" only when historyDaysLimitKnown is true.
    val historyDaysLimit: Int? = null,
    val historyDaysLimitKnown: Boolean? = null,
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
    override val serverId: Long?,
    val projectLocalId: String,
    val name: String,
    val description: String?,
    val estimatedBudget: Double?,
    val startDate: String?,
    val endDate: String?,
    val status: String,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
) : SyncedRow

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
    override val serverId: Long?,
    val dailyLogLocalId: String,
    val type: String,
    val summary: String?,
    val createdById: Long?,
    val createdAt: String?,
    val modifiedById: Long?,
    val modifiedAt: String?,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
) : SyncedRow

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
    override val serverId: Long?,
    val projectLocalId: String,
    val name: String,
    val unit: String,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
) : SyncedRow

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
    override val serverId: Long?,
    val entryLocalId: String,
    val materialLocalId: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val supplier: String?,
    val createdAt: String?,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
    val serverQuantity: Double? = null,
) : SyncedRow

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
    override val serverId: Long?,
    val entryLocalId: String,
    val materialLocalId: String,
    val quantity: Double,
    val createdAt: String?,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
    val serverQuantity: Double? = null,
) : SyncedRow

@Entity(
    tableName = "material_stock",
    primaryKeys = ["projectLocalId", "materialServerId"],
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
data class MaterialStockEntity(
    val projectLocalId: String,
    val materialServerId: Long,
    val quantityIn: Double,
    val quantityOut: Double,
)

@Entity(
    tableName = "stock_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["localId"],
            childColumns = ["projectLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StockSnapshotEntity(
    @PrimaryKey val projectLocalId: String,
    val refreshedAt: Long,
    val needsRefresh: Boolean,
)

data class StockMovementRow(
    val materialLocalId: String,
    val quantity: Double,
    val serverQuantity: Double?,
    val syncStatus: SyncStatus,
    val pendingOp: PendingOp,
    val parentDeleting: Boolean,
)

// A justificatif photo, always attached to a PURCHASE entry (backend rejects
// uploads on a WORK entry — EntryTypeMismatchException). `localPath` points at
// a copy of the picked photo under FileKit.filesDir — Room stores only that
// path, never the bytes. Compression happens server-side (see CONTEXTE.md §9
// bis) once Étape 4 actually uploads this row, so the raw bytes are kept
// as-is locally.
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = DailyEntryEntity::class,
            parentColumns = ["localId"],
            childColumns = ["entryLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryLocalId")],
)
data class AttachmentEntity(
    @PrimaryKey val localId: String,
    override val serverId: Long?,
    val entryLocalId: String,
    val localPath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    // Video only — the server-reported length (whole seconds); null for a photo.
    val durationSeconds: Int? = null,
    val uploadedAt: Long,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    val locallyModifiedAt: Long,
    val lastSyncedAt: Long?,
    val remoteUpdatedAt: Long?,
    override val lastSyncError: String?,
    override val serverErrorCode: String? = null,
) : SyncedRow

// Last-known publisher identity for the legal pages (GET /editor-identity),
// one row (id = 0) like plan_usage, so the legal notice stays right offline and
// before sign-in. Each field null = not filled in on the backend yet.
@Entity(tableName = "editor_identity")
data class EditorIdentityEntity(
    @PrimaryKey val id: Int = 0,
    val firstName: String? = null,
    val lastName: String? = null,
    val companyName: String? = null,
    val legalStatus: String? = null,
    val siret: String? = null,
    val address: String? = null,
    val contactEmail: String? = null,
    val vatNumber: String? = null,
    val hostingProviderName: String? = null,
    val hostingProviderAddress: String? = null,
    val refreshedAt: Long,
)
