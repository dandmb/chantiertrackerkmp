package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.dmb.chantiertracker.domain.model.SyncIssueTarget

interface SyncIssueLocalActions {
    suspend fun countLinked(target: SyncIssueTarget, localId: String): Int

    suspend fun removeLocally(target: SyncIssueTarget, localId: String): List<String>

    suspend fun forgetRefusedDelete(target: SyncIssueTarget, localId: String)

    suspend fun restoreKnownServerValue(target: SyncIssueTarget, localId: String): Boolean
}

@Dao
abstract class SyncIssueActionDao : SyncIssueLocalActions {

    @Transaction
    override suspend fun countLinked(target: SyncIssueTarget, localId: String): Int = when (target) {
        SyncIssueTarget.PROJECT -> stageIdsOfProject(localId).sumOf { 1 + linkedToStage(it) } + countMaterialsOfProject(localId)
        SyncIssueTarget.STAGE -> linkedToStage(localId)
        SyncIssueTarget.MATERIAL -> countPurchaseLinesOfMaterial(localId) + countConsumptionLinesOfMaterial(localId)
        SyncIssueTarget.ENTRY -> linkedToEntry(localId)
        SyncIssueTarget.PURCHASE_LINE, SyncIssueTarget.CONSUMPTION_LINE, SyncIssueTarget.ATTACHMENT -> 0
    }

    private suspend fun linkedToStage(stageLocalId: String): Int = entryIdsOfStage(stageLocalId).sumOf { 1 + linkedToEntry(it) }

    private suspend fun linkedToEntry(entryLocalId: String): Int =
        countPurchaseLinesOfEntry(entryLocalId) + countConsumptionLinesOfEntry(entryLocalId) + countAttachmentsOfEntry(entryLocalId)

    @Transaction
    override suspend fun removeLocally(target: SyncIssueTarget, localId: String): List<String> {
        if (!isRemovable(target, localId)) return emptyList()
        val filePaths = mutableListOf<String>()
        when (target) {
            SyncIssueTarget.PROJECT -> {
                stageIdsOfProject(localId).forEach { removeStage(it, filePaths) }
                deleteMaterialsOfProject(localId)
                deleteProject(localId)
            }
            SyncIssueTarget.STAGE -> removeStage(localId, filePaths)
            SyncIssueTarget.MATERIAL -> {
                deletePurchaseLinesOfMaterial(localId)
                deleteConsumptionLinesOfMaterial(localId)
                deleteMaterial(localId)
            }
            SyncIssueTarget.ENTRY -> removeEntry(localId, filePaths)
            SyncIssueTarget.PURCHASE_LINE -> deletePurchaseLine(localId)
            SyncIssueTarget.CONSUMPTION_LINE -> deleteConsumptionLine(localId)
            SyncIssueTarget.ATTACHMENT -> {
                filePaths += attachmentPath(localId)
                deleteAttachment(localId)
            }
        }
        return filePaths
    }

    private suspend fun isRemovable(target: SyncIssueTarget, localId: String): Boolean = when (target) {
        SyncIssueTarget.PROJECT -> isRemovableProject(localId)
        SyncIssueTarget.STAGE -> isRemovableStage(localId)
        SyncIssueTarget.MATERIAL -> isRemovableMaterial(localId)
        SyncIssueTarget.ENTRY -> isRemovableEntry(localId)
        SyncIssueTarget.PURCHASE_LINE -> isRemovablePurchaseLine(localId)
        SyncIssueTarget.CONSUMPTION_LINE -> isRemovableConsumptionLine(localId)
        SyncIssueTarget.ATTACHMENT -> isRemovableAttachment(localId)
    } > 0

    private suspend fun removeStage(stageLocalId: String, filePaths: MutableList<String>) {
        entryIdsOfStage(stageLocalId).forEach { removeEntry(it, filePaths) }
        deleteLogsOfStage(stageLocalId)
        deleteStage(stageLocalId)
    }

    private suspend fun removeEntry(entryLocalId: String, filePaths: MutableList<String>) {
        filePaths += attachmentPathsOfEntry(entryLocalId)
        deletePurchaseLinesOfEntry(entryLocalId)
        deleteConsumptionLinesOfEntry(entryLocalId)
        deleteAttachmentsOfEntry(entryLocalId)
        val dailyLogLocalId = logIdOfEntry(entryLocalId)
        deleteEntry(entryLocalId)
        dailyLogLocalId?.let { deleteLogIfOnlyLocalAndEmpty(it) }
    }

    @Transaction
    override suspend fun forgetRefusedDelete(target: SyncIssueTarget, localId: String) = when (target) {
        SyncIssueTarget.PROJECT -> forgetProjectRefusedDelete(localId)
        SyncIssueTarget.STAGE -> forgetStageRefusedDelete(localId)
        SyncIssueTarget.MATERIAL -> forgetMaterialRefusedDelete(localId)
        SyncIssueTarget.ENTRY -> forgetEntryRefusedDelete(localId)
        SyncIssueTarget.PURCHASE_LINE -> forgetPurchaseLineRefusedDelete(localId)
        SyncIssueTarget.CONSUMPTION_LINE -> forgetConsumptionLineRefusedDelete(localId)
        SyncIssueTarget.ATTACHMENT -> forgetAttachmentRefusedDelete(localId)
    }

    @Transaction
    override suspend fun restoreKnownServerValue(target: SyncIssueTarget, localId: String): Boolean =
        target == SyncIssueTarget.CONSUMPTION_LINE && restoreConsumptionLineQuantity(localId) > 0

    @Query(
        "UPDATE consumption_lines SET quantity = serverQuantity, syncStatus = 'SYNCED', pendingOp = 'NONE', lastSyncError = NULL, serverErrorCode = NULL " +
            "WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND pendingOp = 'UPDATE' AND serverId IS NOT NULL AND serverQuantity IS NOT NULL",
    )
    protected abstract suspend fun restoreConsumptionLineQuantity(localId: String): Int

    @Query("SELECT localId FROM stages WHERE projectLocalId = :projectLocalId")
    protected abstract suspend fun stageIdsOfProject(projectLocalId: String): List<String>

    @Query("SELECT e.localId FROM daily_entries e JOIN daily_logs l ON l.localId = e.dailyLogLocalId WHERE l.stageLocalId = :stageLocalId")
    protected abstract suspend fun entryIdsOfStage(stageLocalId: String): List<String>

    @Query("SELECT COUNT(*) FROM materials WHERE projectLocalId = :projectLocalId")
    protected abstract suspend fun countMaterialsOfProject(projectLocalId: String): Int

    @Query("SELECT COUNT(*) FROM purchase_lines WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun countPurchaseLinesOfEntry(entryLocalId: String): Int

    @Query("SELECT COUNT(*) FROM consumption_lines WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun countConsumptionLinesOfEntry(entryLocalId: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun countAttachmentsOfEntry(entryLocalId: String): Int

    @Query("SELECT COUNT(*) FROM purchase_lines WHERE materialLocalId = :materialLocalId")
    protected abstract suspend fun countPurchaseLinesOfMaterial(materialLocalId: String): Int

    @Query("SELECT COUNT(*) FROM consumption_lines WHERE materialLocalId = :materialLocalId")
    protected abstract suspend fun countConsumptionLinesOfMaterial(materialLocalId: String): Int

    @Query("SELECT localPath FROM attachments WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun attachmentPathsOfEntry(entryLocalId: String): List<String>

    @Query("SELECT localPath FROM attachments WHERE localId = :localId")
    protected abstract suspend fun attachmentPath(localId: String): List<String>

    @Query("SELECT dailyLogLocalId FROM daily_entries WHERE localId = :entryLocalId")
    protected abstract suspend fun logIdOfEntry(entryLocalId: String): String?

    @Query("DELETE FROM purchase_lines WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun deletePurchaseLinesOfEntry(entryLocalId: String)

    @Query("DELETE FROM consumption_lines WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun deleteConsumptionLinesOfEntry(entryLocalId: String)

    @Query("DELETE FROM attachments WHERE entryLocalId = :entryLocalId")
    protected abstract suspend fun deleteAttachmentsOfEntry(entryLocalId: String)

    @Query("DELETE FROM daily_entries WHERE localId = :localId")
    protected abstract suspend fun deleteEntry(localId: String)

    @Query(
        "DELETE FROM daily_logs WHERE localId = :localId AND serverId IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM daily_entries WHERE dailyLogLocalId = :localId)",
    )
    protected abstract suspend fun deleteLogIfOnlyLocalAndEmpty(localId: String)

    @Query("DELETE FROM daily_logs WHERE stageLocalId = :stageLocalId")
    protected abstract suspend fun deleteLogsOfStage(stageLocalId: String)

    @Query("DELETE FROM stages WHERE localId = :localId")
    protected abstract suspend fun deleteStage(localId: String)

    @Query("DELETE FROM purchase_lines WHERE materialLocalId = :materialLocalId")
    protected abstract suspend fun deletePurchaseLinesOfMaterial(materialLocalId: String)

    @Query("DELETE FROM consumption_lines WHERE materialLocalId = :materialLocalId")
    protected abstract suspend fun deleteConsumptionLinesOfMaterial(materialLocalId: String)

    @Query("DELETE FROM materials WHERE localId = :localId")
    protected abstract suspend fun deleteMaterial(localId: String)

    @Query("DELETE FROM materials WHERE projectLocalId = :projectLocalId")
    protected abstract suspend fun deleteMaterialsOfProject(projectLocalId: String)

    @Query("DELETE FROM projects WHERE localId = :localId")
    protected abstract suspend fun deleteProject(localId: String)

    @Query("DELETE FROM purchase_lines WHERE localId = :localId")
    protected abstract suspend fun deletePurchaseLine(localId: String)

    @Query("DELETE FROM consumption_lines WHERE localId = :localId")
    protected abstract suspend fun deleteConsumptionLine(localId: String)

    @Query("DELETE FROM attachments WHERE localId = :localId")
    protected abstract suspend fun deleteAttachment(localId: String)

    @Query("SELECT COUNT(*) FROM projects WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovableProject(localId: String): Int

    @Query("SELECT COUNT(*) FROM stages WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovableStage(localId: String): Int

    @Query("SELECT COUNT(*) FROM materials WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovableMaterial(localId: String): Int

    @Query("SELECT COUNT(*) FROM daily_entries WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovableEntry(localId: String): Int

    @Query("SELECT COUNT(*) FROM purchase_lines WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovablePurchaseLine(localId: String): Int

    @Query("SELECT COUNT(*) FROM consumption_lines WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovableConsumptionLine(localId: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE localId = :localId AND syncStatus = 'CONFLICTED' AND (serverId IS NULL OR COALESCE(lastSyncError, '') = 'DELETED_ON_SERVER')")
    protected abstract suspend fun isRemovableAttachment(localId: String): Int

    @Query("UPDATE projects SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetProjectRefusedDelete(localId: String)

    @Query("UPDATE stages SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetStageRefusedDelete(localId: String)

    @Query("UPDATE materials SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetMaterialRefusedDelete(localId: String)

    @Query("UPDATE daily_entries SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetEntryRefusedDelete(localId: String)

    @Query("UPDATE purchase_lines SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetPurchaseLineRefusedDelete(localId: String)

    @Query("UPDATE consumption_lines SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetConsumptionLineRefusedDelete(localId: String)

    @Query("UPDATE attachments SET lastSyncError = NULL, serverErrorCode = NULL WHERE localId = :localId AND syncStatus = 'SYNCED' AND pendingOp = 'NONE' AND lastSyncError = 'REJECTED'")
    protected abstract suspend fun forgetAttachmentRefusedDelete(localId: String)
}
