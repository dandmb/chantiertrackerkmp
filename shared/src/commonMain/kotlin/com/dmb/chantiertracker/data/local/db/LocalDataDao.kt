package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Everything in Room that belongs to the signed-in account, as one unit (ADR-69): counted before
 * a voluntary sign-out, erased when another account signs in on this device. Room 2.8 has no
 * `clearAllTables()` in common code, hence the explicit deletes — children before parents, so no
 * foreign key is ever violated mid-transaction.
 */
@Dao
abstract class LocalDataDao {

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM projects WHERE syncStatus != 'SYNCED') +
            (SELECT COUNT(*) FROM stages WHERE syncStatus != 'SYNCED') +
            (SELECT COUNT(*) FROM materials WHERE syncStatus != 'SYNCED') +
            (SELECT COUNT(*) FROM daily_entries WHERE syncStatus != 'SYNCED') +
            (SELECT COUNT(*) FROM purchase_lines WHERE syncStatus != 'SYNCED') +
            (SELECT COUNT(*) FROM consumption_lines WHERE syncStatus != 'SYNCED') +
            (SELECT COUNT(*) FROM attachments WHERE syncStatus != 'SYNCED')
        """,
    )
    abstract suspend fun countUnsynced(): Int

    @Query("DELETE FROM attachments") protected abstract suspend fun deleteAttachments()
    @Query("DELETE FROM purchase_lines") protected abstract suspend fun deletePurchaseLines()
    @Query("DELETE FROM consumption_lines") protected abstract suspend fun deleteConsumptionLines()
    @Query("DELETE FROM daily_entries") protected abstract suspend fun deleteEntries()
    @Query("DELETE FROM daily_logs") protected abstract suspend fun deleteLogs()
    @Query("DELETE FROM materials") protected abstract suspend fun deleteMaterials()
    @Query("DELETE FROM invitations") protected abstract suspend fun deleteInvitations()
    @Query("DELETE FROM project_members") protected abstract suspend fun deleteMembers()
    @Query("DELETE FROM stages") protected abstract suspend fun deleteStages()
    @Query("DELETE FROM projects") protected abstract suspend fun deleteProjects()
    @Query("DELETE FROM plan_usage") protected abstract suspend fun deletePlanUsage()
    @Query("DELETE FROM editor_identity") protected abstract suspend fun deleteEditorIdentity()
    @Query("DELETE FROM material_stock") protected abstract suspend fun deleteStockCounters()
    @Query("DELETE FROM stock_snapshots") protected abstract suspend fun deleteStockSnapshots()

    @Transaction
    open suspend fun eraseAll() {
        deleteAttachments()
        deletePurchaseLines()
        deleteConsumptionLines()
        deleteEntries()
        deleteLogs()
        deleteMaterials()
        deleteInvitations()
        deleteMembers()
        deleteStockCounters()
        deleteStockSnapshots()
        deleteStages()
        deleteProjects()
        deletePlanUsage()
        deleteEditorIdentity()
    }
}
