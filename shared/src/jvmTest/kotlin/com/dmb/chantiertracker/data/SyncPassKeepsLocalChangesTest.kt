package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.SyncedRow
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.repository.ConsumptionLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.PurchaseLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.data.sync.parseServerTimestampMillis
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.ServerConsumptionLine
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerPurchaseLine
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncPassKeepsLocalChangesTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val fileStore = FakeAttachmentFileStore()
    private val scope = AppCoroutineScope()
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
        backgroundSync = FakeBackgroundSync(),
        awaitedServerVersions = db.syncIssueActionDao(),
    )
    private val savesWithoutStartingAPass = FakeSyncer()
    private val projects = ProjectRepositoryImpl(db.projectDao(), savesWithoutStartingAPass, scope)
    private val stages = StageRepositoryImpl(db.stageDao(), savesWithoutStartingAPass, scope)
    private val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), savesWithoutStartingAPass, scope)
    private val purchaseLines = PurchaseLineRepositoryImpl(db.purchaseLineDao(), savesWithoutStartingAPass, scope)
    private val consumptionLines = ConsumptionLineRepositoryImpl(db.consumptionLineDao(), savesWithoutStartingAPass, scope)
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, savesWithoutStartingAPass, scope)
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest fun close() = db.close()

    private suspend fun aSyncedSite() {
        backend.nextLogId = 2_000L
        backend.nextEntryId = 3_000L
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        backend.seedPurchaseLine(ServerPurchaseLine(id = 5000, entryId = 900, materialId = 7, quantity = 100.0, unitPrice = 6.0))
        backend.seedConsumptionLine(ServerConsumptionLine(id = 6000, entryId = 901, materialId = 7, quantity = 10.0))
        val synced = SyncStatus.SYNCED
        val none = PendingOp.NONE
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced, remoteUpdatedAt = parseServerTimestampMillis("2026-01-01T09:00:00")))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = none, syncStatus = synced))
        db.purchaseLineDao().upsert(localPurchaseLine("pl5000", entryLocalId = "e900", materialLocalId = "m7", quantity = 100.0, unitPrice = 6.0, serverId = 5000, pendingOp = none, syncStatus = synced))
        db.consumptionLineDao().upsert(localConsumptionLine("cl6000", entryLocalId = "e901", materialLocalId = "m7", quantity = 10.0, serverId = 6000, pendingOp = none, syncStatus = synced))
    }

    private fun HttpRequestData.isA(method: String, pathEnd: String) = this.method.value == method && url.encodedPath.endsWith(pathEnd)

    private fun whileTheServerHandles(method: String, pathEnd: String, refused: Boolean = false, userAction: suspend () -> Unit) {
        var done = false
        if (refused) backend.refuseOnce = { it.isA(method, pathEnd) }
        backend.beforeHandle = { request ->
            if (!done && request.isA(method, pathEnd)) {
                done = true
                userAction()
            }
        }
    }

    private inner class Kind(
        val name: String,
        val target: SyncIssueTarget,
        val createPath: String,
        val updatePath: String,
        val existingLocalId: String,
        val sent: Any,
        val savedMeanwhile: Any,
        val create: suspend (Any) -> String,
        val save: suspend (String, Any) -> Unit,
        val delete: (suspend (String) -> Unit)?,
        val row: suspend (String) -> SyncedRow?,
        val localValue: suspend (String) -> Any?,
        val onServer: () -> List<Any?>,
    )

    private fun projectInput(name: String) = UpdateProjectInput(name, null, null, "EUR", "Europe/Paris", ProjectStatus.IN_PROGRESS)
    private fun stageInput(name: String) = UpdateStageInput(name, null, null, null, null, StageStatus.IN_PROGRESS)

    private val kinds = listOf(
        Kind(
            "project", SyncIssueTarget.PROJECT, "/projects", "/projects/5", "p5", "Envoyé", "Saisi pendant l'envoi",
            create = { projects.createProject(CreateProjectInput(it as String, null, null, "EUR", "Europe/Paris")) },
            save = { id, value -> projects.updateProject(id, projectInput(value as String)) },
            delete = { projects.deleteProject(it) },
            row = { db.projectDao().findByLocalId(it) },
            localValue = { db.projectDao().findByLocalId(it)?.name },
            onServer = { backend.projects.map { it.name } },
        ),
        Kind(
            "stage", SyncIssueTarget.STAGE, "/projects/5/stages", "/stages/90", "st90", "Envoyé", "Saisi pendant l'envoi",
            create = { stages.createStage(CreateStageInput("p5", it as String, null, null, null, null)) },
            save = { id, value -> stages.updateStage(id, stageInput(value as String)) },
            delete = { stages.deleteStage(it) },
            row = { db.stageDao().findByLocalId(it) },
            localValue = { db.stageDao().findByLocalId(it)?.name },
            onServer = { backend.stages.map { it.name } },
        ),
        Kind(
            "entry", SyncIssueTarget.ENTRY, "/stages/90/logs/2026-09-07/works", "/entries/900", "e900", "Envoyé", "Saisi pendant l'envoi",
            create = { summary ->
                logs.createWorkEntry("st90", "2026-09-07").entryLocalId.also { logs.updateEntry(it, summary as String) }
            },
            save = { id, value -> logs.updateEntry(id, value as String) },
            delete = null,
            row = { db.dailyEntryDao().findByLocalId(it) },
            localValue = { db.dailyEntryDao().findByLocalId(it)?.summary },
            onServer = { backend.entries.map { it.summary } },
        ),
        Kind(
            "purchase line", SyncIssueTarget.PURCHASE_LINE, "/entries/900/purchase-lines", "/purchase-lines/5000", "pl5000", 5.0, 2.0,
            create = { purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", it as Double, 6.0, null)) },
            save = { id, value -> purchaseLines.updateLine(id, UpdatePurchaseLineInput(value as Double, 6.0, null)) },
            delete = { purchaseLines.deleteLine(it) },
            row = { db.purchaseLineDao().findByLocalId(it) },
            localValue = { db.purchaseLineDao().findByLocalId(it)?.quantity },
            onServer = { backend.purchaseLines.map { it.quantity } },
        ),
        Kind(
            "consumption line", SyncIssueTarget.CONSUMPTION_LINE, "/entries/901/consumption-lines", "/consumption-lines/6000", "cl6000", 5.0, 2.0,
            create = { consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", it as Double)) },
            save = { id, value -> consumptionLines.updateLine(id, UpdateConsumptionLineInput(value as Double)) },
            delete = { consumptionLines.deleteLine(it) },
            row = { db.consumptionLineDao().findByLocalId(it) },
            localValue = { db.consumptionLineDao().findByLocalId(it)?.quantity },
            onServer = { backend.consumptionLines.map { it.quantity } },
        ),
    )

    private fun eachKind(block: suspend (Kind) -> Unit) = runTest {
        aSyncedSite()
        kinds.forEach { kind ->
            block(kind)
            backend.beforeHandle = null
            backend.refuseOnce = null
        }
    }

    private suspend fun toReview() = issues.observeIssues().first().map { it.key }

    private fun state(row: SyncedRow?) = row?.let { listOf(it.syncStatus, it.pendingOp, it.lastSyncError, it.serverErrorCode) }

    @Test
    fun a_line_corrected_while_its_refused_creation_is_sent_again_keeps_the_correction() = runTest {
        aSyncedSite()
        db.consumptionLineDao().upsert(
            localConsumptionLine("cl-refused", entryLocalId = "e901", materialLocalId = "m7", quantity = 5.0, syncStatus = SyncStatus.CONFLICTED)
                .copy(lastSyncError = SyncError.REJECTED, serverErrorCode = "INSUFFICIENT_STOCK"),
        )
        backend.lineWriteConflict = true
        whileTheServerHandles("POST", "/entries/901/consumption-lines") {
            consumptionLines.updateLine("cl-refused", UpdateConsumptionLineInput(2.0))
        }

        engine.syncNow()

        val line = db.consumptionLineDao().findByLocalId("cl-refused")!!
        assertEquals(2.0, line.quantity, "the correction saved during the call survives the refusal of the old value")
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), state(line), "the refusal of the old value is not put back on the corrected line")
        assertTrue("CONSUMPTION_LINE:cl-refused" !in toReview())

        backend.lineWriteConflict = false
        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(listOf(10.0, 2.0), backend.consumptionLines.map { it.quantity })
        assertEquals(SyncStatus.SYNCED, db.consumptionLineDao().findByLocalId("cl-refused")!!.syncStatus)
    }

    @Test
    fun an_element_edited_while_its_creation_is_accepted_keeps_the_edit_and_sends_it_next_without_creating_a_second_one() = eachKind { kind ->
        val before = kind.onServer().size
        val id = kind.create(kind.sent)
        whileTheServerHandles("POST", kind.createPath) { kind.save(id, kind.savedMeanwhile) }

        engine.syncNow()

        assertEquals(kind.savedMeanwhile, kind.localValue(id), "${kind.name}: what was saved during the call is still there")
        val row = kind.row(id)!!
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.UPDATE, null, null), state(row), "${kind.name}: still waiting, now as a change of the created element")
        assertNotNull(row.serverId, "${kind.name}: the server id is kept, so the next pass does not create it again")
        assertEquals(kind.sent, kind.onServer().last(), "${kind.name}: the server holds what was sent")

        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(before + 1, kind.onServer().size, "${kind.name}: created once")
        assertEquals(kind.savedMeanwhile, kind.onServer().last(), "${kind.name}: the server now holds the edit")
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, PendingOp.NONE, null, null), state(kind.row(id)))
    }

    @Test
    fun an_element_edited_while_its_creation_is_refused_stays_waiting_with_the_edit_and_carries_no_refusal() = eachKind { kind ->
        val before = kind.onServer().size
        val id = kind.create(kind.sent)
        whileTheServerHandles("POST", kind.createPath, refused = true) { kind.save(id, kind.savedMeanwhile) }

        engine.syncNow()

        assertEquals(kind.savedMeanwhile, kind.localValue(id), kind.name)
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), state(kind.row(id)), "${kind.name}: the refusal was of the old value")
        assertTrue("${kind.target}:$id" !in toReview(), "${kind.name}: not listed to review")

        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(before + 1, kind.onServer().size, kind.name)
        assertEquals(kind.savedMeanwhile, kind.onServer().last(), "${kind.name}: the edit is what reaches the server")
    }

    @Test
    fun an_element_edited_while_its_change_is_accepted_keeps_the_newer_edit_and_sends_it_next() = eachKind { kind ->
        val id = kind.existingLocalId
        kind.save(id, kind.sent)
        whileTheServerHandles("PATCH", kind.updatePath) { kind.save(id, kind.savedMeanwhile) }

        engine.syncNow()

        assertEquals(kind.savedMeanwhile, kind.localValue(id), "${kind.name}: the newer edit is not replaced by the value just sent")
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.UPDATE, null, null), state(kind.row(id)), "${kind.name}: never shown as synchronised")
        assertTrue(kind.sent in kind.onServer(), "${kind.name}: the server holds what was sent")

        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertTrue(kind.savedMeanwhile in kind.onServer() && kind.sent !in kind.onServer(), "${kind.name}: ${kind.onServer()}")
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, PendingOp.NONE, null, null), state(kind.row(id)))
    }

    @Test
    fun an_element_edited_while_its_change_is_refused_stays_waiting_with_the_newer_edit_and_carries_no_refusal() = eachKind { kind ->
        val id = kind.existingLocalId
        kind.save(id, kind.sent)
        whileTheServerHandles("PATCH", kind.updatePath, refused = true) { kind.save(id, kind.savedMeanwhile) }

        engine.syncNow()

        assertEquals(kind.savedMeanwhile, kind.localValue(id), kind.name)
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.UPDATE, null, null), state(kind.row(id)), "${kind.name}: the refused value is gone, the refusal with it")
        assertTrue("${kind.target}:$id" !in toReview(), kind.name)

        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertTrue(kind.savedMeanwhile in kind.onServer(), "${kind.name}: ${kind.onServer()}")
    }

    @Test
    fun an_element_deleted_while_its_creation_is_accepted_does_not_come_back_and_does_not_stay_on_the_server() = eachKind { kind ->
        val delete = kind.delete ?: return@eachKind
        val before = kind.onServer()
        val id = kind.create(kind.sent)
        whileTheServerHandles("POST", kind.createPath) { delete(id) }

        engine.syncNow()
        backend.beforeHandle = null
        engine.syncNow()

        assertNull(kind.row(id), "${kind.name}: the deleted element is not brought back by the answer to its creation")
        assertEquals(before, kind.onServer(), "${kind.name}: nothing of it is left on the server")
        assertTrue("${kind.target}:$id" !in toReview(), kind.name)
    }

    @Test
    fun an_element_deleted_while_its_creation_is_refused_stays_deleted() = eachKind { kind ->
        val delete = kind.delete ?: return@eachKind
        val before = kind.onServer()
        val id = kind.create(kind.sent)
        whileTheServerHandles("POST", kind.createPath, refused = true) { delete(id) }

        engine.syncNow()

        assertNull(kind.row(id), "${kind.name}: the refusal does not bring the deleted element back")
        assertTrue("${kind.target}:$id" !in toReview(), kind.name)
        backend.beforeHandle = null
        engine.syncNow()
        assertEquals(before, kind.onServer(), kind.name)
    }

    @Test
    fun an_element_deleted_while_its_change_is_accepted_or_refused_is_deleted_on_the_server_next() {
        for (refused in listOf(false, true)) {
            for (index in kinds.indices) {
                val site = SyncPassKeepsLocalChangesTest()
                try {
                    runTest {
                        site.aSyncedSite()
                        val kind = site.kinds[index]
                        val delete = kind.delete ?: return@runTest
                        val id = kind.existingLocalId
                        kind.save(id, kind.sent)
                        site.whileTheServerHandles("PATCH", kind.updatePath, refused) { delete(id) }

                        site.engine.syncNow()

                        assertEquals(
                            listOf<Any?>(SyncStatus.PENDING, PendingOp.DELETE, null, null), site.state(kind.row(id)),
                            "${kind.name}, refused=$refused: the deletion asked during the call is what waits",
                        )
                        site.backend.beforeHandle = null
                        site.engine.syncNow()
                        assertNull(kind.row(id), "${kind.name}, refused=$refused")
                        assertTrue(kind.sent !in kind.onServer() && kind.savedMeanwhile !in kind.onServer(), "${kind.name}, refused=$refused: ${kind.onServer()}")
                    }
                } finally {
                    site.close()
                }
            }
        }
    }

    @Test
    fun a_line_edited_while_its_change_or_its_creation_is_accepted_remembers_what_the_server_now_holds() = runTest {
        aSyncedSite()
        purchaseLines.updateLine("pl5000", UpdatePurchaseLineInput(50.0, 6.0, null))
        consumptionLines.updateLine("cl6000", UpdateConsumptionLineInput(5.0))
        val created = purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 7.0, 6.0, null))
        var saved = 0
        backend.beforeHandle = { request ->
            when {
                request.isA("PATCH", "/purchase-lines/5000") && saved and 1 == 0 -> { saved = saved or 1; purchaseLines.updateLine("pl5000", UpdatePurchaseLineInput(40.0, 6.0, null)) }
                request.isA("PATCH", "/consumption-lines/6000") && saved and 2 == 0 -> { saved = saved or 2; consumptionLines.updateLine("cl6000", UpdateConsumptionLineInput(4.0)) }
                request.isA("POST", "/entries/900/purchase-lines") && saved and 4 == 0 -> { saved = saved or 4; purchaseLines.updateLine(created, UpdatePurchaseLineInput(8.0, 6.0, null)) }
            }
        }

        engine.syncNow()

        assertEquals(7, saved, "the three saves happened during the three calls")
        assertEquals(listOf<Any?>(40.0, 50.0), db.purchaseLineDao().findByLocalId("pl5000")!!.let { listOf(it.quantity, it.serverQuantity) }, "40 on the device, 50 on the server")
        assertEquals(listOf<Any?>(4.0, 5.0), db.consumptionLineDao().findByLocalId("cl6000")!!.let { listOf(it.quantity, it.serverQuantity) })
        assertEquals(listOf<Any?>(8.0, 7.0), db.purchaseLineDao().findByLocalId(created)!!.let { listOf(it.quantity, it.serverQuantity) })

        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(listOf(40.0, 8.0), backend.purchaseLines.map { it.quantity })
        assertEquals(listOf(4.0), backend.consumptionLines.map { it.quantity })
        assertEquals(listOf<Any?>(40.0, 40.0), db.purchaseLineDao().findByLocalId("pl5000")!!.let { listOf(it.quantity, it.serverQuantity) })
    }

    private suspend fun aDuplicateOfAMaterialTheServerRenamed() {
        backend.seedMaterial(ServerMaterial(id = 8, projectId = 5, name = "Sable", unit = "t"))
        db.materialDao().upsert(localMaterial("m8", projectLocalId = "p5", name = "sable", unit = "t", serverId = 8, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.materialDao().upsert(localMaterial("m-dup", projectLocalId = "p5", name = "Sable", unit = "t"))
    }

    @Test
    fun lines_saved_edited_and_deleted_while_a_duplicate_material_is_being_merged_all_follow_the_surviving_material() = runTest {
        aSyncedSite()
        aDuplicateOfAMaterialTheServerRenamed()
        val before = purchaseLines.createLine("e900", CreatePurchaseLineInput("m-dup", 1.0, 6.0, null))
        val removed = purchaseLines.createLine("e900", CreatePurchaseLineInput("m-dup", 9.0, 6.0, null))
        var duringThePost = ""
        var duringTheRead = ""
        var steps = 0
        backend.beforeHandle = { request ->
            when {
                request.isA("POST", "/projects/5/materials") && steps == 0 -> {
                    steps = 1
                    duringThePost = purchaseLines.createLine("e900", CreatePurchaseLineInput("m-dup", 3.0, 6.0, null))
                    purchaseLines.updateLine(before, UpdatePurchaseLineInput(2.0, 6.0, null))
                    purchaseLines.deleteLine(removed)
                }
                request.isA("GET", "/projects/5/materials") && steps == 1 -> {
                    steps = 2
                    duringTheRead = consumptionLines.createLine("e901", CreateConsumptionLineInput("m-dup", 4.0))
                }
            }
        }

        assertEquals(SyncOutcome.Synced, engine.syncNow())
        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(2, steps, "the saves happened during the refused creation and during the read of the namesake")
        assertEquals(listOf("m7" to "Ciment", "m8" to "Sable"), db.materialDao().findForProject("p5").map { it.localId to it.name }.sortedBy { it.first }, "one material left, the server one")
        assertNull(db.purchaseLineDao().findByLocalId(removed), "the line deleted meanwhile stays deleted")
        assertEquals(
            listOf<Any?>("m8" to 2.0, "m8" to 3.0, "m8" to 4.0),
            listOf(
                db.purchaseLineDao().findByLocalId(before)!!.let { it.materialLocalId to it.quantity },
                db.purchaseLineDao().findByLocalId(duringThePost)!!.let { it.materialLocalId to it.quantity },
                db.consumptionLineDao().findByLocalId(duringTheRead)!!.let { it.materialLocalId to it.quantity },
            ),
            "every line saved meanwhile now belongs to the surviving material, with what was typed",
        )
        assertEquals(listOf(100.0, 2.0, 3.0), backend.purchaseLines.map { it.quantity }, "and reached the server once each")
        assertEquals(listOf(8L, 8L), backend.purchaseLines.drop(1).map { it.materialId })
        assertEquals(listOf(10.0 to 7L, 4.0 to 8L), backend.consumptionLines.map { it.quantity to it.materialId })
    }

    @Test
    fun a_line_saved_right_after_the_merge_with_the_material_that_was_merged_away_is_kept_under_the_surviving_material() = runTest {
        aSyncedSite()
        aDuplicateOfAMaterialTheServerRenamed()
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertNull(db.materialDao().findByLocalId("m-dup"), "the duplicate was merged into the server material")

        val purchase = runCatching { purchaseLines.createLine("e900", CreatePurchaseLineInput("m-dup", 4.0, 6.0, null)) }
        val consumption = runCatching { consumptionLines.createLine("e901", CreateConsumptionLineInput("m-dup", 1.0)) }

        assertNull(purchase.exceptionOrNull(), "the form still held the merged material: its save must not fail")
        assertNull(consumption.exceptionOrNull())
        assertEquals(listOf<Any?>("m8", 4.0, SyncStatus.PENDING), db.purchaseLineDao().findByLocalId(purchase.getOrThrow())!!.let { listOf(it.materialLocalId, it.quantity, it.syncStatus) })
        assertEquals("m8", db.consumptionLineDao().findByLocalId(consumption.getOrThrow())!!.materialLocalId)
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(listOf(100.0 to 7L, 4.0 to 8L), backend.purchaseLines.map { it.quantity to it.materialId }, "the line reaches the server under the surviving material")
    }

    @Test
    fun a_material_merged_twice_in_a_row_still_leads_a_late_line_to_the_last_survivor() = runTest {
        aSyncedSite()
        aDuplicateOfAMaterialTheServerRenamed()
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        backend.seedMaterial(ServerMaterial(id = 9, projectId = 5, name = "Sable fin", unit = "t"))
        db.materialDao().upsert(localMaterial("m9", projectLocalId = "p5", name = "sable fin", unit = "t", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        backend.materials.first { it.id == 8L }.name = "Gravier"
        backend.materials.first { it.id == 9L }.name = "Sable"
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))

        val late = runCatching { purchaseLines.createLine("e900", CreatePurchaseLineInput("m-dup", 4.0, 6.0, null)) }

        assertNull(late.exceptionOrNull())
        val material = db.purchaseLineDao().findByLocalId(late.getOrThrow())!!.materialLocalId
        assertNotNull(db.materialDao().findByLocalId(material), "whatever happened to the materials since, the line points at one that exists")
    }

    @Test
    fun a_photo_deleted_while_it_is_uploaded_does_not_come_back_and_leaves_neither_file_nor_server_copy() = runTest {
        aSyncedSite()
        val photo = attachments.addAttachment("e900", ByteArray(16), "bon.jpg", "image/jpeg")
        whileTheServerHandles("POST", "/entries/900/attachments") { attachments.deleteAttachment(photo.localId) }

        engine.syncNow()
        backend.beforeHandle = null
        engine.syncNow()

        assertNull(db.attachmentDao().findByLocalId(photo.localId), "the row is not brought back pointing at a deleted file")
        assertTrue(backend.attachments.isEmpty(), "the copy the server just accepted is deleted there too: ${backend.attachments.map { it.id }}")
        assertTrue(fileStore.storedPaths.isEmpty())
        assertTrue(toReview().isEmpty())
    }

    @Test
    fun a_photo_deleted_while_its_upload_is_refused_stays_deleted_and_is_not_listed_as_a_refused_file() = runTest {
        aSyncedSite()
        val photo = attachments.addAttachment("e900", ByteArray(16), "bon.jpg", "image/jpeg")
        backend.attachmentUploadRejection = io.ktor.http.HttpStatusCode.PayloadTooLarge to "Fichier trop volumineux."
        backend.attachmentUploadRejectionCode = "ATTACHMENT_TOO_LARGE"
        whileTheServerHandles("POST", "/entries/900/attachments") { attachments.deleteAttachment(photo.localId) }

        engine.syncNow()

        assertNull(db.attachmentDao().findByLocalId(photo.localId))
        assertTrue(toReview().isEmpty(), "no refused file left to review: ${toReview()}")
        assertTrue(fileStore.storedPaths.isEmpty())
    }

    @Test
    fun a_line_saved_while_undo_my_change_reads_the_server_keeps_what_was_just_saved() = runTest {
        aSyncedSite()
        db.purchaseLineDao().upsert(
            db.purchaseLineDao().findByLocalId("pl5000")!!.copy(
                quantity = 3.0, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED,
                lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "STOCK_CONSUMED", serverQuantity = 100.0,
            ),
        )
        val refusedChange = issues.observeIssues().first().single { it.localId == "pl5000" }
        whileTheServerHandles("GET", "/entries/900/purchase-lines") { purchaseLines.updateLine("pl5000", UpdatePurchaseLineInput(4.0, 6.0, null)) }

        issues.revert(refusedChange)

        val line = db.purchaseLineDao().findByLocalId("pl5000")!!
        assertEquals(4.0, line.quantity, "the server value does not replace what was saved during the read")
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.UPDATE, null, null), state(line))
    }

    @Test
    fun a_stage_renamed_while_its_awaited_server_version_is_read_keeps_the_new_name() = runTest {
        aSyncedSite()
        db.stageDao().upsert(db.stageDao().findByLocalId("st90")!!.copy(lastSyncError = SyncError.REJECTED, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
        connectivity.setOnline(false)
        issues.acknowledge(issues.observeIssues().first().single { it.localId == "st90" })
        connectivity.setOnline(true)
        whileTheServerHandles("GET", "/stages/90") { stages.updateStage("st90", stageInput("Renommée pendant la lecture")) }

        engine.syncNow()
        backend.beforeHandle = null
        engine.syncNow()

        assertEquals("Renommée pendant la lecture", db.stageDao().findByLocalId("st90")!!.name)
        assertEquals(listOf("Renommée pendant la lecture"), backend.stages.map { it.name }, "and the rename reaches the server")
    }
}
