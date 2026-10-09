package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.repository.ConsumptionLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.repository.MaterialRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.PurchaseLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.session.RoomUnsyncedWriteCounter
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncIssuesReachTheDomainTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val syncer = FakeSyncer()
    private val scope = AppCoroutineScope()
    private val projects = ProjectRepositoryImpl(db.projectDao(), syncer, scope)
    private val stages = StageRepositoryImpl(db.stageDao(), syncer, scope)
    private val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), syncer, scope)
    private val materials = MaterialRepositoryImpl(db.materialDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.stockDao(), syncer, scope)
    private val purchaseLines = PurchaseLineRepositoryImpl(db.purchaseLineDao(), syncer, scope)
    private val consumptionLines = ConsumptionLineRepositoryImpl(db.consumptionLineDao(), syncer, scope)
    private val attachments =
        AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), FakeProjectBackend().attachmentApi(), FakeAttachmentFileStore(), syncer, scope)

    @AfterTest fun close() = db.close()

    private val blocked = SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT)

    @Test
    fun a_project_over_the_plan_limit_and_the_stage_and_material_created_under_it() = runTest {
        db.projectDao().upsert(localProject("p-fine", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.projectDao().upsert(
            localProject("p-over", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.PLAN_LIMIT).copy(serverErrorCode = "PLAN_LIMIT_EXCEEDED"),
        )
        db.stageDao().upsert(localStage("st-under", projectLocalId = "p-over"))
        db.materialDao().upsert(localMaterial("m-under", projectLocalId = "p-over"))
        db.stageDao().upsert(localStage("st-fine", projectLocalId = "p-fine"))

        val listed = projects.observeProjects().first().associateBy { it.localId }
        assertEquals(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PLAN_LIMIT, "PLAN_LIMIT_EXCEEDED"), listed.getValue("p-over").syncIssue)
        assertNull(listed.getValue("p-fine").syncIssue)
        assertEquals(blocked, stages.observeStages("p-over").first().single().syncIssue)
        assertEquals(blocked, materials.observeMaterials("p-over").first().single().syncIssue)
        assertNull(stages.observeStages("p-fine").first().single().syncIssue, "a stage simply waiting to be sent is not an issue")
    }

    @Test
    fun an_entry_refused_on_a_suspended_project_and_the_line_and_photo_waiting_on_it() = runTest {
        db.projectDao().upsert(localProject("p", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st", projectLocalId = "p", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l", stageLocalId = "st"))
        db.materialDao().upsert(localMaterial("m", projectLocalId = "p", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyEntryDao().upsert(
            localDailyEntry("e-refused", dailyLogLocalId = "l", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED)
                .copy(serverErrorCode = "PROJECT_OR_STAGE_INACTIVE"),
        )
        db.dailyEntryDao().upsert(
            localDailyEntry("e-work", dailyLogLocalId = "l", type = "WORK", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED)
                .copy(serverErrorCode = "PROJECT_OR_STAGE_INACTIVE"),
        )
        db.purchaseLineDao().upsert(localPurchaseLine("pl", entryLocalId = "e-refused", materialLocalId = "m"))
        db.consumptionLineDao().upsert(localConsumptionLine("cl", entryLocalId = "e-work", materialLocalId = "m"))
        db.attachmentDao().upsert(localAttachment("a", entryLocalId = "e-refused"))

        val refused = SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PROJECT_OR_STAGE_INACTIVE, "PROJECT_OR_STAGE_INACTIVE")
        assertEquals(listOf(refused, refused), logs.observeLog("l").first()!!.entries.map { it.syncIssue })
        assertEquals(refused, logs.observeEntry("e-refused").first()!!.syncIssue)
        assertEquals(blocked, purchaseLines.observeLines("e-refused").first().single().syncIssue)
        assertEquals(blocked, consumptionLines.observeLines("e-work").first().single().syncIssue)
        assertEquals(blocked, attachments.observeAttachments("e-refused").first().single().syncIssue)
    }

    @Test
    fun a_refused_edit_a_refused_delete_and_a_refused_file() = runTest {
        db.projectDao().upsert(localProject("p", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st", projectLocalId = "p", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l", stageLocalId = "st"))
        db.materialDao().upsert(localMaterial("m", projectLocalId = "p", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyEntryDao().upsert(
            localDailyEntry("e", dailyLogLocalId = "l", serverId = 3, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED, lastSyncError = SyncError.REJECTED)
                .copy(serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"),
        )
        db.purchaseLineDao().upsert(
            localPurchaseLine("pl-edit", entryLocalId = "e", materialLocalId = "m", quantity = 3.0, serverId = 60, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED)
                .copy(lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "STOCK_CONSUMED", serverQuantity = 10.0),
        )
        db.attachmentDao().upsert(
            localAttachment("a-heavy", entryLocalId = "e").copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.FILE_REFUSED, serverErrorCode = "ATTACHMENT_TOO_LARGE"),
        )

        assertEquals(
            SyncIssue(SyncIssueKind.DELETE_REFUSED, RefusalReason.INSUFFICIENT_ROLE, "PROJECT_INSUFFICIENT_ROLE"),
            logs.observeLog("l").first()!!.entries.single().syncIssue,
        )
        assertEquals(
            SyncIssue(SyncIssueKind.UPDATE_REFUSED, RefusalReason.STOCK_CONSUMED, "STOCK_CONSUMED"),
            purchaseLines.observeLines("e").first().single().syncIssue,
        )
        assertEquals(
            SyncIssue(SyncIssueKind.REFUSED, RefusalReason.FILE_REFUSED, "ATTACHMENT_TOO_LARGE"),
            attachments.observeAttachments("e").first().single().syncIssue,
        )
    }

    @Test
    fun one_entry_left_on_a_project_deleted_on_the_server_counts_as_one_entry_not_three() = runTest {
        db.projectDao().upsert(localProject("p-ghost", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        db.stageDao().upsert(localStage("st-ghost", projectLocalId = "p-ghost", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        db.dailyLogDao().upsert(localDailyLog("l", stageLocalId = "st-ghost"))
        db.dailyEntryDao().upsert(localDailyEntry("e-orphan", dailyLogLocalId = "l", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        val counter = RoomUnsyncedWriteCounter(db.localDataDao())

        assertEquals(UnsentWrites(entries = 1), counter.unsentByKind())
        assertEquals(3, counter.countUnsynced(), "the erase guard of ADR-69 still sees the two ghosts")
    }

    @Test
    fun a_deletion_refused_mention_is_still_there_after_the_app_is_restarted() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("chantier-restart")
        val path = dir.resolve("restart.db").toString()
        try {
            val beforeRestart = Room.databaseBuilder<AppDatabase>(name = path).buildChantierDatabase()
            beforeRestart.projectDao().upsert(localProject("p", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
            beforeRestart.stageDao().upsert(
                localStage("st", projectLocalId = "p", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED, lastSyncError = SyncError.REJECTED)
                    .copy(serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"),
            )
            beforeRestart.close()

            val afterRestart = Room.databaseBuilder<AppDatabase>(name = path).buildChantierDatabase()
            try {
                val shown = StageRepositoryImpl(afterRestart.stageDao(), syncer, scope).observeStages("p").first().single()
                assertEquals(SyncIssue(SyncIssueKind.DELETE_REFUSED, RefusalReason.INSUFFICIENT_ROLE, "PROJECT_INSUFFICIENT_ROLE"), shown.syncIssue)
            } finally {
                afterRestart.close()
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
