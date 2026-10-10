package com.dmb.chantiertracker.data

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.room.execSQL
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.ConsumptionLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.actions
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.repository.RevertOutcome
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.data.local.db.AwaitedRow
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.ServerConsumptionLine
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerPurchaseLine
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncIssueActionsThroughTheEngineTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val fileStore = FakeAttachmentFileStore()
    private val scope = AppCoroutineScope()
    private val backgroundSync = FakeBackgroundSync()
    private val engine = SyncEngine(
        dao = db.projectDao(),
        api = backend.api(),
        stageDao = db.stageDao(),
        stageApi = backend.stageApi(),
        materialDao = db.materialDao(),
        materialAdoptionDao = db.materialAdoptionDao(),
        materialApi = backend.materialApi(),
        dailyLogDao = db.dailyLogDao(),
        dailyEntryDao = db.dailyEntryDao(),
        dailyLogApi = backend.dailyLogApi(),
        purchaseLineDao = db.purchaseLineDao(),
        purchaseLineApi = backend.purchaseLineApi(),
        consumptionLineDao = db.consumptionLineDao(),
        consumptionLineApi = backend.consumptionLineApi(),
        attachmentDao = db.attachmentDao(),
        attachmentApi = backend.attachmentApi(),
        attachmentFileStore = fileStore,
        invitationDao = db.invitationDao(),
        invitationApi = backend.invitationApi(),
        stockApi = backend.stockApi(),
        stockDao = db.stockDao(),
        connectivity = connectivity,
        syncState = SyncStateHolder(),
        scope = scope,
        backgroundSync = backgroundSync,
        awaitedServerVersions = db.syncIssueActionDao(),
    )
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )
    private val consumptionLines = ConsumptionLineRepositoryImpl(db.consumptionLineDao(), engine, scope)

    @AfterTest fun close() = db.close()

    private fun writesSent() = backend.receivedMethods.filter { !it.startsWith("GET ") }

    private fun sent(method: String, pathSuffix: String) =
        backend.receivedMethods.count { it.startsWith("$method ") && it.endsWith(pathSuffix) }

    private suspend fun item(localId: String) = issues.observeIssues().first().single { it.localId == localId }

    private suspend fun aSyncedSite() {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Résumé du serveur"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        backend.seedPurchaseLine(ServerPurchaseLine(id = 5000, entryId = 900, materialId = 7, quantity = 12.0, unitPrice = 6.0, supplier = "Point P"))
        backend.seedConsumptionLine(ServerConsumptionLine(id = 6000, entryId = 901, materialId = 7, quantity = 2.0))
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Résumé du serveur", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", serverId = 901, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.purchaseLineDao().upsert(
            localPurchaseLine("pl5000", entryLocalId = "e900", materialLocalId = "m7", quantity = 12.0, unitPrice = 6.0, supplier = "Point P", serverId = 5000, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        db.consumptionLineDao().upsert(localConsumptionLine("cl6000", entryLocalId = "e901", materialLocalId = "m7", quantity = 2.0, serverId = 6000, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    private suspend fun aRefusedDayWithItsLinesAndPhoto(): String {
        db.dailyLogDao().upsert(localDailyLog("l-new", stageLocalId = "st90", date = "2026-09-06"))
        db.dailyEntryDao().upsert(localDailyEntry("e-refused", dailyLogLocalId = "l-new", type = "PURCHASE", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = "DUPLICATE_ENTRY"))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-child-1", entryLocalId = "e-refused", materialLocalId = "m7", quantity = 3.0))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-child-2", entryLocalId = "e-refused", materialLocalId = "m7", quantity = 4.0))
        val path = fileStore.save(ByteArray(8), "facture.jpg")
        db.attachmentDao().upsert(localAttachment("a-child", entryLocalId = "e-refused", localPath = path))
        return path
    }

    private suspend fun aRefusedChangeOfThePurchaseLine() {
        db.purchaseLineDao().upsert(
            db.purchaseLineDao().findByLocalId("pl5000")!!.copy(
                quantity = 3.0, unitPrice = 9.0, totalPrice = 27.0, supplier = "Autre", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED,
                lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "STOCK_CONSUMED", serverQuantity = 12.0,
            ),
        )
    }

    @Test
    fun discarding_a_refused_entry_removes_it_with_its_lines_and_photo_and_nothing_of_it_is_ever_sent() = runTest {
        aSyncedSite()
        val path = aRefusedDayWithItsLinesAndPhoto()
        val counts = mutableListOf<Int>()
        val watching = backgroundScope.launch { issues.observeIssueCount().collect { counts += it } }
        val entry = item("e-refused")
        assertEquals(3, issues.linkedCount(entry), "two lines and one photo go with it")
        assertEquals(4, issues.observeIssues().first().size)

        issues.discard(entry)

        assertNull(db.dailyEntryDao().findByLocalId("e-refused"))
        assertEquals(listOf<Any?>(null, null, null, null), listOf(
            db.purchaseLineDao().findByLocalId("pl-child-1"), db.purchaseLineDao().findByLocalId("pl-child-2"),
            db.attachmentDao().findByLocalId("a-child"), db.dailyLogDao().findByLocalId("l-new"),
        ))
        assertTrue(path !in fileStore.storedPaths, "the local file of the photo is deleted too")
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(emptyList(), writesSent(), "nothing of the discarded entry is left in the queue")
        assertNotNull(db.dailyEntryDao().findByLocalId("e900"), "what the server holds is untouched")
        assertEquals(12.0, db.purchaseLineDao().findByLocalId("pl5000")!!.quantity)
        testScheduler.advanceUntilIdle()
        watching.cancel()
        assertEquals(1, counts.first())
        assertEquals(0, counts.last(), "the badge count follows by itself: $counts")
    }

    @Test
    fun a_discard_interrupted_half_way_removes_nothing_at_all() = runTest {
        aSyncedSite()
        val path = aRefusedDayWithItsLinesAndPhoto()
        val entry = item("e-refused")
        db.useWriterConnection { it.execSQL("CREATE TRIGGER process_dies BEFORE DELETE ON daily_entries BEGIN SELECT RAISE(ABORT, 'the process died'); END") }

        assertFails { issues.discard(entry) }

        assertNotNull(db.dailyEntryDao().findByLocalId("e-refused"))
        assertEquals(
            listOf("pl-child-1", "pl-child-2"), db.purchaseLineDao().findForEntry("e-refused").map { it.localId }.sorted(),
            "the lines deleted before the interruption are back: no parent is left without its children, no child without its parent",
        )
        assertNotNull(db.attachmentDao().findByLocalId("a-child"))
        assertTrue(path in fileStore.storedPaths, "no file is deleted for rows that are still there")
        assertEquals(4, issues.observeIssues().first().size)

        db.useWriterConnection { it.execSQL("DROP TRIGGER process_dies") }
        issues.discard(entry)
        assertTrue(issues.observeIssues().first().isEmpty(), "the same action, done again, completes")
    }

    @Test
    fun a_double_tap_on_discard_removes_once_and_fails_nowhere() = runTest {
        aSyncedSite()
        aRefusedDayWithItsLinesAndPhoto()
        val entry = item("e-refused")

        listOf(async { issues.discard(entry) }, async { issues.discard(entry) }, async { issues.discard(entry) }).awaitAll()

        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(1, fileStore.deletedPaths.size, "the photo file is deleted once")
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(emptyList(), writesSent())
    }

    @Test
    fun a_process_killed_after_the_rows_are_removed_and_before_the_file_is_deleted_leaves_nothing_in_the_queue() = runTest {
        aSyncedSite()
        aRefusedDayWithItsLinesAndPhoto()
        val entry = item("e-refused")
        fileStore.failOnDelete = true

        issues.discard(entry)

        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(emptyList(), db.attachmentDao().findPending().map { it.localId })
        assertEquals(emptyList(), db.purchaseLineDao().findPending().map { it.localId })
        fileStore.failOnDelete = false
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(emptyList(), writesSent())
    }

    @Test
    fun discarding_a_refused_file_deletes_the_row_and_the_local_file() = runTest {
        aSyncedSite()
        val path = fileStore.save(ByteArray(8), "lourde.jpg")
        db.attachmentDao().upsert(
            localAttachment("a-heavy", entryLocalId = "e900", localPath = path)
                .copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.FILE_REFUSED, serverErrorCode = "ATTACHMENT_TOO_LARGE"),
        )

        issues.discard(item("a-heavy"))

        assertNull(db.attachmentDao().findByLocalId("a-heavy"))
        assertTrue(fileStore.storedPaths.isEmpty())
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(0, sent("POST", "/entries/900/attachments"))
    }

    @Test
    fun fixing_a_refused_line_offline_puts_it_back_in_the_queue_once_and_it_leaves_when_the_network_returns() = runTest {
        aSyncedSite()
        db.consumptionLineDao().upsert(localConsumptionLine("cl-too-much", entryLocalId = "e901", materialLocalId = "m7", quantity = 50.0))
        backend.lineWriteConflict = true
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        val refused = item("cl-too-much")
        assertEquals(SyncIssueKind.REFUSED, refused.issue.kind)
        assertEquals(1, issues.observeIssueCount().first())

        connectivity.setOnline(false)
        consumptionLines.updateLine("cl-too-much", UpdateConsumptionLineInput(1.0))
        consumptionLines.updateLine("cl-too-much", UpdateConsumptionLineInput(1.0))

        val queued = db.consumptionLineDao().findPending()
        assertEquals(listOf("cl-too-much"), queued.map { it.localId }, "in the queue once, even saved twice")
        assertEquals<List<Any?>>(listOf(SyncStatus.PENDING, PendingOp.CREATE, null, 1.0), queued.single().let { listOf(it.syncStatus, it.pendingOp, it.lastSyncError, it.quantity) })
        assertEquals(0, issues.observeIssueCount().first(), "offline, the corrected entry is waiting to be sent, no longer to review")
        assertTrue(issues.observeIssues().first().isEmpty())

        connectivity.setOnline(true)
        backend.lineWriteConflict = false
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(SyncStatus.SYNCED, db.consumptionLineDao().findByLocalId("cl-too-much")!!.syncStatus)
        assertEquals(2, sent("POST", "/entries/901/consumption-lines"), "the refused attempt, then the corrected one; no duplicate")
        assertEquals(1, backend.consumptionLines.count { it.quantity == 1.0 })
    }

    @Test
    fun reverting_a_refused_change_online_reads_the_server_then_restores_every_field_without_sending_anything() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()
        val change = item("pl5000")
        assertEquals(SyncIssueKind.UPDATE_REFUSED, change.issue.kind)

        assertEquals(RevertOutcome.RESTORED, issues.revert(change))

        val line = db.purchaseLineDao().findByLocalId("pl5000")!!
        assertEquals<List<Any?>>(listOf(12.0, 6.0, "Point P", SyncStatus.SYNCED, PendingOp.NONE, null, null), listOf(line.quantity, line.unitPrice, line.supplier, line.syncStatus, line.pendingOp, line.lastSyncError, line.serverErrorCode))
        assertEquals(emptyList(), writesSent(), "reverting never sends the refused change")
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(12.0, backend.purchaseLines.single { it.id == 5000L }.quantity)

        val requests = backend.receivedMethods.size
        assertEquals(RevertOutcome.RESTORED, issues.revert(change), "a second tap finds the change already reverted")
        assertEquals(requests, backend.receivedMethods.size, "and asks the server nothing more")
    }

    @Test
    fun reverting_offline_a_change_whose_server_value_is_unknown_changes_nothing() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()
        val before = db.purchaseLineDao().findByLocalId("pl5000")
        connectivity.setOnline(false)

        assertEquals(RevertOutcome.NEEDS_CONNECTION, issues.revert(item("pl5000")))

        assertEquals(before, db.purchaseLineDao().findByLocalId("pl5000"))
        assertEquals(emptyList(), backend.receivedMethods)
    }

    @Test
    fun a_revert_the_server_cannot_answer_leaves_the_change_refused_and_listed() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()
        val before = db.purchaseLineDao().findByLocalId("pl5000")
        backend.listPageFailure = 0 to HttpStatusCode.InternalServerError

        assertEquals(RevertOutcome.FAILED, issues.revert(item("pl5000")))

        assertEquals(before, db.purchaseLineDao().findByLocalId("pl5000"))
        assertEquals(1, issues.observeIssues().first().size)
        assertEquals(emptyList(), db.purchaseLineDao().findPending().map { it.localId }, "it is not put back in the queue either")
    }

    @Test
    fun reverting_a_consumption_line_works_offline_from_the_value_kept_on_the_device() = runTest {
        aSyncedSite()
        db.consumptionLineDao().upsert(
            db.consumptionLineDao().findByLocalId("cl6000")!!.copy(
                quantity = 50.0, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED,
                lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "INSUFFICIENT_STOCK", serverQuantity = 2.0,
            ),
        )
        connectivity.setOnline(false)

        assertEquals(RevertOutcome.RESTORED, issues.revert(item("cl6000")))

        val line = db.consumptionLineDao().findByLocalId("cl6000")!!
        assertEquals<List<Any?>>(listOf(2.0, SyncStatus.SYNCED, PendingOp.NONE, null), listOf(line.quantity, line.syncStatus, line.pendingOp, line.lastSyncError))
        assertEquals(emptyList(), backend.receivedMethods)
    }

    @Test
    fun reverting_a_refused_change_of_a_project_a_stage_a_material_and_an_entry_restores_what_the_server_holds() = runTest {
        aSyncedSite()
        val refusedChange = { code: String -> Triple(SyncStatus.CONFLICTED, SyncError.UPDATE_REFUSED, code) }
        val (status, error, code) = refusedChange("PROJECT_INSUFFICIENT_ROLE")
        db.projectDao().upsert(db.projectDao().findByLocalId("p5")!!.copy(name = "Renommé", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        db.stageDao().upsert(db.stageDao().findByLocalId("st90")!!.copy(name = "Renommée", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        db.materialDao().upsert(db.materialDao().findByLocalId("m7")!!.copy(name = "Ciment gris", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        db.dailyEntryDao().upsert(db.dailyEntryDao().findByLocalId("e900")!!.copy(summary = "Résumé local", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        assertEquals(4, issues.observeIssues().first().size)

        listOf("p5", "st90", "m7", "e900").forEach { assertEquals(RevertOutcome.RESTORED, issues.revert(item(it)), it) }

        assertEquals(
            listOf("Villa", "Gros œuvre", "Ciment", "Résumé du serveur"),
            listOf(db.projectDao().findByLocalId("p5")!!.name, db.stageDao().findByLocalId("st90")!!.name, db.materialDao().findByLocalId("m7")!!.name, db.dailyEntryDao().findByLocalId("e900")!!.summary),
        )
        assertEquals(
            List(4) { SyncStatus.SYNCED },
            listOf(db.projectDao().findByLocalId("p5")!!.syncStatus, db.stageDao().findByLocalId("st90")!!.syncStatus, db.materialDao().findByLocalId("m7")!!.syncStatus, db.dailyEntryDao().findByLocalId("e900")!!.syncStatus),
        )
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(emptyList(), writesSent())
        assertEquals(12.0, db.purchaseLineDao().findByLocalId("pl5000")!!.quantity, "the children of a reverted parent are untouched")
    }

    @Test
    fun acknowledging_an_entry_deleted_on_the_server_purges_it_and_what_waited_under_it() = runTest {
        aSyncedSite()
        db.dailyEntryDao().upsert(db.dailyEntryDao().findByLocalId("e901")!!.copy(summary = "Modifié", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        db.consumptionLineDao().upsert(localConsumptionLine("cl-new", entryLocalId = "e901", materialLocalId = "m7", quantity = 1.0).copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        val gone = item("e901")
        assertEquals(SyncIssueKind.DELETED_ON_SERVER, gone.issue.kind)

        issues.acknowledge(gone)

        assertNull(db.dailyEntryDao().findByLocalId("e901"))
        assertNull(db.consumptionLineDao().findByLocalId("cl-new"))
        assertTrue(issues.observeIssues().first().isEmpty())
        assertNotNull(db.dailyEntryDao().findByLocalId("e900"))
    }

    @Test
    fun acknowledging_a_creation_refused_for_an_unknown_reason_purges_it_and_it_is_never_sent_again() = runTest {
        aSyncedSite()
        db.stageDao().upsert(localStage("st-unknown", projectLocalId = "p5", name = "Bardage", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = "A_CODE_FROM_A_NEWER_SERVER"))
        val unknown = item("st-unknown")
        assertEquals(RefusalReason.UNKNOWN, unknown.issue.reason)

        issues.acknowledge(unknown)

        assertNull(db.stageDao().findByLocalId("st-unknown"))
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(0, sent("POST", "/projects/5/stages"))
        assertEquals(0, issues.observeIssueCount().first())
    }

    private suspend fun aRefusedDeleteOfTheStageStillHoldingAnOldName() {
        db.stageDao().upsert(db.stageDao().findByLocalId("st90")!!.copy(name = "Ancien nom", lastSyncError = SyncError.REJECTED, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
    }

    private suspend fun stage() = db.stageDao().findByLocalId("st90")

    private suspend fun awaitedRows() = db.syncIssueActionDao().rowsAwaitingServerVersion()

    @Test
    fun acknowledging_a_refused_delete_online_brings_the_element_back_with_the_server_version() = runTest {
        aSyncedSite()
        aRefusedDeleteOfTheStageStillHoldingAnOldName()
        val refusedDelete = item("st90")
        assertEquals(SyncIssueKind.DELETE_REFUSED, refusedDelete.issue.kind)

        issues.acknowledge(refusedDelete)

        val stage = stage()!!
        assertEquals<List<Any?>>(listOf("Gros œuvre", SyncStatus.SYNCED, PendingOp.NONE, null, null, 90L), listOf(stage.name, stage.syncStatus, stage.pendingOp, stage.lastSyncError, stage.serverErrorCode, stage.serverId))
        assertEquals(1, sent("GET", "/stages/90"), "the server version is read at once")
        assertEquals(emptyList(), awaitedRows())
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(emptyList(), writesSent())

        issues.acknowledge(refusedDelete)
        assertEquals(1, sent("GET", "/stages/90"), "a second tap asks the server nothing more")
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertTrue(issues.observeIssues().first().isEmpty(), "the mention does not come back with the next pull")
        assertNotNull(db.dailyEntryDao().findByLocalId("e900"))
    }

    @Test
    fun acknowledging_a_refused_delete_offline_keeps_the_last_known_version_and_refreshes_it_when_the_network_returns() = runTest {
        aSyncedSite()
        aRefusedDeleteOfTheStageStillHoldingAnOldName()
        connectivity.setOnline(false)

        val refusedDelete = item("st90")
        issues.acknowledge(refusedDelete)
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
        assertEquals(1, backgroundSync.expeditedCount, "the refresh is handed to the system scheduler")
        issues.acknowledge(refusedDelete)

        assertEquals<List<Any?>>(listOf("Ancien nom", SyncStatus.SYNCED, PendingOp.NONE, 90L), stage()!!.let { listOf(it.name, it.syncStatus, it.pendingOp, it.serverId) }, "the last known version stays visible")
        assertTrue(issues.observeIssues().first().isEmpty(), "the mention is dropped")
        assertEquals(emptyList(), backend.receivedMethods)
        assertEquals(listOf(AwaitedRow(SyncIssueTarget.STAGE, "st90")), awaitedRows(), "the refresh is written down, so it survives the death of the process")

        backend.stages.single { it.id == 90L }.name = "Renommée sur le serveur"
        connectivity.setOnline(true)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals<List<Any?>>(listOf("Renommée sur le serveur", null), stage()!!.let { listOf(it.name, it.lastSyncError) })
        assertEquals(emptyList(), awaitedRows())
        assertEquals(emptyList(), writesSent())
        assertTrue(issues.observeIssues().first().isEmpty())
    }

    @Test
    fun a_refused_delete_of_each_kind_of_element_comes_back_with_what_the_server_holds() = runTest {
        aSyncedSite()
        val rejected = SyncError.REJECTED
        db.projectDao().upsert(db.projectDao().findByLocalId("p5")!!.copy(name = "Vieux", lastSyncError = rejected, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
        db.materialDao().upsert(db.materialDao().findByLocalId("m7")!!.copy(name = "Vieux", lastSyncError = rejected, serverErrorCode = "STOCK_CONSUMED"))
        db.dailyEntryDao().upsert(db.dailyEntryDao().findByLocalId("e900")!!.copy(summary = "Vieux", lastSyncError = rejected, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
        db.purchaseLineDao().upsert(db.purchaseLineDao().findByLocalId("pl5000")!!.copy(quantity = 1.0, lastSyncError = rejected, serverErrorCode = "STOCK_CONSUMED"))
        db.consumptionLineDao().upsert(db.consumptionLineDao().findByLocalId("cl6000")!!.copy(quantity = 1.0, lastSyncError = rejected, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
        val path = fileStore.save(ByteArray(8), "facture.jpg")
        db.attachmentDao().upsert(localAttachment("a-kept", entryLocalId = "e900", localPath = path, serverId = 70, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED).copy(lastSyncError = rejected, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
        val listed = issues.observeIssues().first()
        assertEquals(6, listed.size)

        listed.forEach { issues.acknowledge(it) }

        assertEquals<List<Any?>>(
            listOf("Villa", "Ciment", "Résumé du serveur", 12.0, 2.0),
            listOf(
                db.projectDao().findByLocalId("p5")!!.name, db.materialDao().findByLocalId("m7")!!.name, db.dailyEntryDao().findByLocalId("e900")!!.summary,
                db.purchaseLineDao().findByLocalId("pl5000")!!.quantity, db.consumptionLineDao().findByLocalId("cl6000")!!.quantity,
            ),
        )
        assertNull(db.attachmentDao().findByLocalId("a-kept")!!.lastSyncError)
        assertTrue(path in fileStore.storedPaths, "the file the server refused to delete stays on the device")
        assertEquals(emptyList(), awaitedRows())
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(emptyList(), writesSent())
    }

    @Test
    fun an_element_edited_while_its_server_version_is_awaited_keeps_the_edit() = runTest {
        aSyncedSite()
        aRefusedDeleteOfTheStageStillHoldingAnOldName()
        connectivity.setOnline(false)
        issues.acknowledge(item("st90"))
        db.stageDao().upsert(stage()!!.copy(name = "Saisi hors ligne", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        connectivity.setOnline(true)

        assertEquals(SyncOutcome.Synced, engine.restoreServerVersion(SyncIssueTarget.STAGE, "st90"))
        assertEquals("Saisi hors ligne", stage()!!.name, "the server version never replaces an unsent edit")
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals("Saisi hors ligne", stage()!!.name)
        assertEquals("Saisi hors ligne", backend.stages.single { it.id == 90L }.name, "the edit is what reaches the server")
        assertEquals(emptyList(), awaitedRows())
    }

    @Test
    fun an_awaited_element_gone_from_the_server_meanwhile_leaves_the_device() = runTest {
        aSyncedSite()
        aRefusedDeleteOfTheStageStillHoldingAnOldName()
        backend.stages.removeAll { it.id == 90L }

        issues.acknowledge(item("st90"))

        assertNull(stage(), "nothing local was unsent under it")
        assertEquals(emptyList(), awaitedRows())
        assertTrue(issues.observeIssues().first().isEmpty())
    }

    @Test
    fun a_refresh_the_server_cannot_answer_stays_awaited_and_does_not_stop_the_pass() = runTest {
        aSyncedSite()
        aRefusedDeleteOfTheStageStillHoldingAnOldName()
        connectivity.setOnline(false)
        issues.acknowledge(item("st90"))
        backend.seed(ServerProject(id = 6, name = "Immeuble"))
        connectivity.setOnline(true)
        var stageUnreachable = true
        backend.beforeHandle = { request -> if (stageUnreachable && request.url.encodedPath.endsWith("/stages/90")) throw java.io.IOException("coupure") }

        engine.syncNow()

        assertEquals(listOf(AwaitedRow(SyncIssueTarget.STAGE, "st90")), awaitedRows())
        assertEquals("Ancien nom", stage()!!.name)
        assertNotNull(db.projectDao().findByServerId(6), "the rest of the pass went on")

        stageUnreachable = false
        engine.syncNow()
        assertEquals("Gros œuvre", stage()!!.name)
        assertEquals(emptyList(), awaitedRows())
    }

    @Test
    fun an_awaited_element_the_account_may_no_longer_read_stops_being_awaited_and_keeps_its_last_known_version() = runTest {
        aSyncedSite()
        aRefusedDeleteOfTheStageStillHoldingAnOldName()
        connectivity.setOnline(false)
        issues.acknowledge(item("st90"))
        connectivity.setOnline(true)
        backend.forbiddenOnServer += Regex("/stages/90$")

        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(emptyList(), awaitedRows(), "it is not asked for at every pass for ever")
        assertEquals<List<Any?>>(listOf("Ancien nom", null), stage()!!.let { listOf(it.name, it.lastSyncError) })
    }

    @Test
    fun reverting_a_change_of_an_element_gone_from_the_server_turns_it_into_deleted_on_the_server_with_got_it() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()
        backend.purchaseLines.removeAll { it.id == 5000L }

        assertEquals(RevertOutcome.GONE_ON_SERVER, issues.revert(item("pl5000")))

        val gone = item("pl5000")
        assertEquals(SyncIssueKind.DELETED_ON_SERVER, gone.issue.kind)
        assertEquals(listOf(SyncIssueAction.ACKNOWLEDGE), gone.actions)
        assertNull(gone.issue.serverCode)
        assertEquals(3.0, db.purchaseLineDao().findByLocalId("pl5000")!!.quantity, "what the user typed is still there to read")
        assertEquals(emptyList(), db.purchaseLineDao().findPending().map { it.localId }, "it is never sent again")
        assertEquals(1, issues.observeIssueCount().first())
        assertEquals(emptyList(), writesSent())

        issues.acknowledge(gone)
        issues.acknowledge(gone)
        assertNull(db.purchaseLineDao().findByLocalId("pl5000"))
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(emptyList(), writesSent())
    }

    @Test
    fun reverting_a_change_of_a_project_a_stage_a_material_and_an_entry_gone_from_the_server_marks_each_as_deleted_there() = runTest {
        aSyncedSite()
        val refused = { code: String -> Triple(SyncStatus.CONFLICTED, SyncError.UPDATE_REFUSED, code) }
        val (status, error, code) = refused("PROJECT_INSUFFICIENT_ROLE")
        db.materialDao().upsert(db.materialDao().findByLocalId("m7")!!.copy(name = "Ciment gris", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        db.dailyEntryDao().upsert(db.dailyEntryDao().findByLocalId("e900")!!.copy(summary = "Résumé local", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        db.stageDao().upsert(db.stageDao().findByLocalId("st90")!!.copy(name = "Renommée", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        db.projectDao().upsert(db.projectDao().findByLocalId("p5")!!.copy(name = "Renommé", pendingOp = PendingOp.UPDATE, syncStatus = status, lastSyncError = error, serverErrorCode = code))
        backend.materials.removeAll { it.id == 7L }
        backend.entries.removeAll { it.id == 900L }
        assertEquals(RevertOutcome.GONE_ON_SERVER, issues.revert(item("m7")))
        assertEquals(RevertOutcome.GONE_ON_SERVER, issues.revert(item("e900")))
        backend.stages.removeAll { it.id == 90L }
        assertEquals(RevertOutcome.GONE_ON_SERVER, issues.revert(item("st90")))
        backend.projects.removeAll { it.id == 5L }
        assertEquals(RevertOutcome.GONE_ON_SERVER, issues.revert(item("p5")))

        listOf("m7", "e900", "st90", "p5").forEach {
            assertEquals(SyncIssueKind.DELETED_ON_SERVER, item(it).issue.kind, it)
            assertEquals(listOf(SyncIssueAction.ACKNOWLEDGE), item(it).actions, it)
        }
        assertEquals("Renommé", db.projectDao().findByLocalId("p5")!!.name)
        assertEquals(emptyList(), writesSent())
    }

    @Test
    fun reverting_a_change_of_a_line_whose_whole_entry_is_gone_from_the_server_marks_the_line_as_deleted_there() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()
        db.consumptionLineDao().upsert(
            db.consumptionLineDao().findByLocalId("cl6000")!!.copy(quantity = 9.0, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "INSUFFICIENT_STOCK"),
        )
        db.materialDao().upsert(db.materialDao().findByLocalId("m7")!!.copy(name = "Ciment gris", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
        backend.goneOnServer += Regex("/entries/90[01]/")
        backend.goneOnServer += Regex("/projects/5")

        listOf("pl5000", "cl6000", "m7").forEach { assertEquals(RevertOutcome.GONE_ON_SERVER, issues.revert(item(it)), it) }

        listOf("pl5000", "cl6000", "m7").forEach { assertEquals(SyncIssueKind.DELETED_ON_SERVER, item(it).issue.kind, it) }
        assertEquals(emptyList(), writesSent())
    }

    @Test
    fun a_revert_reading_only_part_of_the_server_list_never_concludes_the_element_is_gone() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()
        backend.purchaseLines.removeAll { it.id == 5000L }
        repeat(250) { backend.seedPurchaseLine(ServerPurchaseLine(id = 7000L + it, entryId = 900, materialId = 7, quantity = 1.0, unitPrice = 1.0)) }
        backend.listPageFailure = 1 to HttpStatusCode.InternalServerError

        assertEquals(RevertOutcome.FAILED, issues.revert(item("pl5000")))

        assertEquals(SyncIssueKind.UPDATE_REFUSED, item("pl5000").issue.kind)
    }

    @Test
    fun the_item_of_a_line_knows_the_entry_and_the_currency_its_form_needs() = runTest {
        aSyncedSite()
        aRefusedChangeOfThePurchaseLine()

        val line = item("pl5000")

        assertEquals(listOf("e900", "EUR", "p5"), listOf(line.entryLocalId, line.currency, line.projectLocalId))
    }
}
