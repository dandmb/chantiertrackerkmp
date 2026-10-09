package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import kotlinx.coroutines.flow.Flow

data class SyncIssueRow(
    val target: SyncIssueTarget,
    val localId: String,
    override val serverId: Long?,
    override val syncStatus: SyncStatus,
    override val pendingOp: PendingOp,
    override val lastSyncError: String?,
    override val serverErrorCode: String?,
    val projectLocalId: String,
    val projectName: String,
    val stageLocalId: String?,
    val stageName: String?,
    val dailyLogLocalId: String?,
    val logDate: String?,
    val entryType: String?,
    val label: String?,
    val unit: String?,
    val quantity: Double?,
    val serverQuantity: Double?,
    val entryLocalId: String? = null,
    val currency: String? = null,
) : SyncedRow

@Dao
abstract class SyncIssueDao {

    @Query(
        """
        SELECT 'PURCHASE_LINE' AS target, c.localId AS localId, c.serverId AS serverId, c.syncStatus AS syncStatus, c.pendingOp AS pendingOp,
            c.lastSyncError AS lastSyncError, c.serverErrorCode AS serverErrorCode,
            p.localId AS projectLocalId, p.name AS projectName, s.localId AS stageLocalId, s.name AS stageName,
            l.localId AS dailyLogLocalId, l.date AS logDate, e.type AS entryType,
            m.name AS label, m.unit AS unit, c.quantity AS quantity, c.serverQuantity AS serverQuantity,
            c.entryLocalId AS entryLocalId, p.currency AS currency
        FROM purchase_lines c
            JOIN daily_entries e ON e.localId = c.entryLocalId JOIN daily_logs l ON l.localId = e.dailyLogLocalId
            JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId
            JOIN materials m ON m.localId = c.materialLocalId
        WHERE c.syncStatus != 'SYNCED' OR COALESCE(c.lastSyncError, '') = 'REJECTED'
        UNION ALL
        SELECT 'CONSUMPTION_LINE', c.localId, c.serverId, c.syncStatus, c.pendingOp, c.lastSyncError, c.serverErrorCode,
            p.localId, p.name, s.localId, s.name, l.localId, l.date, e.type, m.name, m.unit, c.quantity, c.serverQuantity, c.entryLocalId, p.currency
        FROM consumption_lines c
            JOIN daily_entries e ON e.localId = c.entryLocalId JOIN daily_logs l ON l.localId = e.dailyLogLocalId
            JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId
            JOIN materials m ON m.localId = c.materialLocalId
        WHERE c.syncStatus != 'SYNCED' OR COALESCE(c.lastSyncError, '') = 'REJECTED'
        UNION ALL
        SELECT 'ATTACHMENT', a.localId, a.serverId, a.syncStatus, a.pendingOp, a.lastSyncError, a.serverErrorCode,
            p.localId, p.name, s.localId, s.name, l.localId, l.date, e.type, a.originalName, NULL, NULL, NULL, a.entryLocalId, p.currency
        FROM attachments a
            JOIN daily_entries e ON e.localId = a.entryLocalId JOIN daily_logs l ON l.localId = e.dailyLogLocalId
            JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId
        WHERE a.syncStatus != 'SYNCED' OR COALESCE(a.lastSyncError, '') = 'REJECTED'
        UNION ALL
        SELECT 'ENTRY', e.localId, e.serverId, e.syncStatus, e.pendingOp, e.lastSyncError, e.serverErrorCode,
            p.localId, p.name, s.localId, s.name, l.localId, l.date, e.type, NULL, NULL, NULL, NULL, e.localId, p.currency
        FROM daily_entries e
            JOIN daily_logs l ON l.localId = e.dailyLogLocalId
            JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId
        WHERE (e.syncStatus != 'SYNCED' OR COALESCE(e.lastSyncError, '') = 'REJECTED')
            AND NOT (COALESCE(e.lastSyncError, '') = 'DELETED_ON_SERVER' AND e.pendingOp = 'NONE' AND e.serverId IS NOT NULL)
        UNION ALL
        SELECT 'MATERIAL', m.localId, m.serverId, m.syncStatus, m.pendingOp, m.lastSyncError, m.serverErrorCode,
            p.localId, p.name, NULL, NULL, NULL, NULL, NULL, m.name, m.unit, NULL, NULL, NULL, p.currency
        FROM materials m JOIN projects p ON p.localId = m.projectLocalId
        WHERE (m.syncStatus != 'SYNCED' OR COALESCE(m.lastSyncError, '') = 'REJECTED')
            AND NOT (COALESCE(m.lastSyncError, '') = 'DELETED_ON_SERVER' AND m.pendingOp = 'NONE' AND m.serverId IS NOT NULL)
        UNION ALL
        SELECT 'STAGE', s.localId, s.serverId, s.syncStatus, s.pendingOp, s.lastSyncError, s.serverErrorCode,
            p.localId, p.name, s.localId, s.name, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, p.currency
        FROM stages s JOIN projects p ON p.localId = s.projectLocalId
        WHERE (s.syncStatus != 'SYNCED' OR COALESCE(s.lastSyncError, '') = 'REJECTED')
            AND NOT (COALESCE(s.lastSyncError, '') = 'DELETED_ON_SERVER' AND s.pendingOp = 'NONE' AND s.serverId IS NOT NULL)
        UNION ALL
        SELECT 'PROJECT', p.localId, p.serverId, p.syncStatus, p.pendingOp, p.lastSyncError, p.serverErrorCode,
            p.localId, p.name, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, p.currency
        FROM projects p
        WHERE (p.syncStatus != 'SYNCED' OR COALESCE(p.lastSyncError, '') = 'REJECTED')
            AND NOT (COALESCE(p.lastSyncError, '') = 'DELETED_ON_SERVER' AND p.pendingOp = 'NONE' AND p.serverId IS NOT NULL)
        """,
    )
    abstract fun observeUnsettled(): Flow<List<SyncIssueRow>>

    open suspend fun sendRefusedUpdateAgain(target: SyncIssueTarget, localId: String) = when (target) {
        SyncIssueTarget.PROJECT -> sendProjectUpdateAgain(localId)
        SyncIssueTarget.STAGE -> sendStageUpdateAgain(localId)
        SyncIssueTarget.MATERIAL -> sendMaterialUpdateAgain(localId)
        SyncIssueTarget.ENTRY -> sendEntryUpdateAgain(localId)
        SyncIssueTarget.PURCHASE_LINE -> sendPurchaseLineUpdateAgain(localId)
        SyncIssueTarget.CONSUMPTION_LINE -> sendConsumptionLineUpdateAgain(localId)
        SyncIssueTarget.ATTACHMENT -> Unit
    }

    open suspend fun freezeUnsentUpdateAgain(target: SyncIssueTarget, localId: String, serverErrorCode: String?) = when (target) {
        SyncIssueTarget.PROJECT -> freezeProjectUpdateAgain(localId, serverErrorCode)
        SyncIssueTarget.STAGE -> freezeStageUpdateAgain(localId, serverErrorCode)
        SyncIssueTarget.MATERIAL -> freezeMaterialUpdateAgain(localId, serverErrorCode)
        SyncIssueTarget.ENTRY -> freezeEntryUpdateAgain(localId, serverErrorCode)
        SyncIssueTarget.PURCHASE_LINE -> freezePurchaseLineUpdateAgain(localId, serverErrorCode)
        SyncIssueTarget.CONSUMPTION_LINE -> freezeConsumptionLineUpdateAgain(localId, serverErrorCode)
        SyncIssueTarget.ATTACHMENT -> Unit
    }

    @Query("UPDATE projects SET syncStatus = 'PENDING', lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND lastSyncError = 'UPDATE_REFUSED'")
    protected abstract suspend fun sendProjectUpdateAgain(localId: String)

    @Query("UPDATE stages SET syncStatus = 'PENDING', lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND lastSyncError = 'UPDATE_REFUSED'")
    protected abstract suspend fun sendStageUpdateAgain(localId: String)

    @Query("UPDATE materials SET syncStatus = 'PENDING', lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND lastSyncError = 'UPDATE_REFUSED'")
    protected abstract suspend fun sendMaterialUpdateAgain(localId: String)

    @Query("UPDATE daily_entries SET syncStatus = 'PENDING', lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND lastSyncError = 'UPDATE_REFUSED'")
    protected abstract suspend fun sendEntryUpdateAgain(localId: String)

    @Query("UPDATE purchase_lines SET syncStatus = 'PENDING', lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND lastSyncError = 'UPDATE_REFUSED'")
    protected abstract suspend fun sendPurchaseLineUpdateAgain(localId: String)

    @Query("UPDATE consumption_lines SET syncStatus = 'PENDING', lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND lastSyncError = 'UPDATE_REFUSED'")
    protected abstract suspend fun sendConsumptionLineUpdateAgain(localId: String)

    @Query("UPDATE projects SET syncStatus = 'CONFLICTED', lastSyncError = 'UPDATE_REFUSED', serverErrorCode = :serverErrorCode WHERE localId = :localId AND syncStatus = 'PENDING' AND pendingOp = 'UPDATE'")
    protected abstract suspend fun freezeProjectUpdateAgain(localId: String, serverErrorCode: String?)

    @Query("UPDATE stages SET syncStatus = 'CONFLICTED', lastSyncError = 'UPDATE_REFUSED', serverErrorCode = :serverErrorCode WHERE localId = :localId AND syncStatus = 'PENDING' AND pendingOp = 'UPDATE'")
    protected abstract suspend fun freezeStageUpdateAgain(localId: String, serverErrorCode: String?)

    @Query("UPDATE materials SET syncStatus = 'CONFLICTED', lastSyncError = 'UPDATE_REFUSED', serverErrorCode = :serverErrorCode WHERE localId = :localId AND syncStatus = 'PENDING' AND pendingOp = 'UPDATE'")
    protected abstract suspend fun freezeMaterialUpdateAgain(localId: String, serverErrorCode: String?)

    @Query("UPDATE daily_entries SET syncStatus = 'CONFLICTED', lastSyncError = 'UPDATE_REFUSED', serverErrorCode = :serverErrorCode WHERE localId = :localId AND syncStatus = 'PENDING' AND pendingOp = 'UPDATE'")
    protected abstract suspend fun freezeEntryUpdateAgain(localId: String, serverErrorCode: String?)

    @Query("UPDATE purchase_lines SET syncStatus = 'CONFLICTED', lastSyncError = 'UPDATE_REFUSED', serverErrorCode = :serverErrorCode WHERE localId = :localId AND syncStatus = 'PENDING' AND pendingOp = 'UPDATE'")
    protected abstract suspend fun freezePurchaseLineUpdateAgain(localId: String, serverErrorCode: String?)

    @Query("UPDATE consumption_lines SET syncStatus = 'CONFLICTED', lastSyncError = 'UPDATE_REFUSED', serverErrorCode = :serverErrorCode WHERE localId = :localId AND syncStatus = 'PENDING' AND pendingOp = 'UPDATE'")
    protected abstract suspend fun freezeConsumptionLineUpdateAgain(localId: String, serverErrorCode: String?)
}
