package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseLineDao : LocalChangesDao<PurchaseLineEntity> {

    @Query("SELECT * FROM purchase_lines WHERE entryLocalId = :entryLocalId AND pendingOp != 'DELETE' ORDER BY locallyModifiedAt")
    fun observeLinesForEntry(entryLocalId: String): Flow<List<PurchaseLineEntity>>

    @Query(
        "SELECT p.materialLocalId, p.quantity, p.serverQuantity, p.syncStatus, p.pendingOp, " +
            "(e.pendingOp = 'DELETE' OR s.pendingOp = 'DELETE') AS parentDeleting " +
            "FROM purchase_lines p " +
            "INNER JOIN daily_entries e ON e.localId = p.entryLocalId " +
            "INNER JOIN daily_logs l ON l.localId = e.dailyLogLocalId " +
            "INNER JOIN stages s ON s.localId = l.stageLocalId " +
            "WHERE s.projectLocalId = :projectLocalId " +
            "AND (p.syncStatus = 'PENDING' OR e.pendingOp = 'DELETE' OR s.pendingOp = 'DELETE')",
    )
    fun observeStockMovements(projectLocalId: String): Flow<List<StockMovementRow>>

    @Query("SELECT * FROM purchase_lines WHERE localId = :localId")
    override suspend fun findByLocalId(localId: String): PurchaseLineEntity?

    @Query("SELECT * FROM purchase_lines WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): PurchaseLineEntity?

    @Query("SELECT * FROM purchase_lines WHERE syncStatus != 'SYNCED' AND (lastSyncError IS NULL OR lastSyncError NOT IN ('DELETED_ON_SERVER', 'UPDATE_REFUSED', 'FILE_REFUSED'))")
    suspend fun findPending(): List<PurchaseLineEntity>

    @Query(
        """
        SELECT c.localId FROM purchase_lines c JOIN daily_entries e ON e.localId = c.entryLocalId JOIN daily_logs l ON l.localId = e.dailyLogLocalId JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId JOIN materials m ON m.localId = c.materialLocalId JOIN projects mp ON mp.localId = m.projectLocalId WHERE c.syncStatus = 'PENDING' AND ((e.serverId IS NULL AND (e.syncStatus = 'CONFLICTED' OR (s.serverId IS NULL AND (s.syncStatus = 'CONFLICTED' OR (p.serverId IS NULL AND p.syncStatus = 'CONFLICTED'))))) OR (m.serverId IS NULL AND (m.syncStatus = 'CONFLICTED' OR (mp.serverId IS NULL AND mp.syncStatus = 'CONFLICTED'))))
        """,
    )
    fun observeBlockedByParent(): Flow<List<String>>

    @Query("SELECT * FROM purchase_lines WHERE entryLocalId = :entryLocalId")
    suspend fun findForEntry(entryLocalId: String): List<PurchaseLineEntity>

    @Upsert
    override suspend fun upsert(row: PurchaseLineEntity)

    @Query("DELETE FROM purchase_lines WHERE localId = :localId")
    override suspend fun deleteByLocalId(localId: String)

    @Query("SELECT keptLocalId FROM material_merges WHERE mergedLocalId = :materialLocalId")
    suspend fun survivorOfMergedMaterial(materialLocalId: String): String?

    @Transaction
    override suspend fun insertNew(row: PurchaseLineEntity): Boolean =
        super.insertNew(row.copy(materialLocalId = survivorOfMergedMaterial(row.materialLocalId) ?: row.materialLocalId))
}
