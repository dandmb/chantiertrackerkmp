package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.LocalDataDao

class FakeLocalDataDao(var unsynced: Int = 0) : LocalDataDao() {
    var eraseCount = 0
        private set

    override suspend fun countUnsynced(): Int = unsynced

    override suspend fun countUnsentByKind() =
        com.dmb.chantiertracker.data.local.db.UnsentCounts(projects = 0, stages = 0, materials = 0, entries = unsynced, lines = 0, attachments = 0)

    override suspend fun eraseAll() {
        eraseCount++
        unsynced = 0
    }

    override suspend fun deleteAttachments() = Unit
    override suspend fun deletePurchaseLines() = Unit
    override suspend fun deleteConsumptionLines() = Unit
    override suspend fun deleteEntries() = Unit
    override suspend fun deleteLogs() = Unit
    override suspend fun deleteMaterials() = Unit
    override suspend fun deleteInvitations() = Unit
    override suspend fun deleteMembers() = Unit
    override suspend fun deleteStages() = Unit
    override suspend fun deleteProjects() = Unit
    override suspend fun deletePlanUsage() = Unit
    override suspend fun deleteEditorIdentity() = Unit
    override suspend fun deleteStockCounters() = Unit
    override suspend fun deleteStockSnapshots() = Unit
}
