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
import com.dmb.chantiertracker.data.repository.MaterialRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.PurchaseLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.data.sync.syncIssue
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.domain.model.actions
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerAttachment
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerRefusal
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PendingDeletesSurviveThePullTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val fileStore = FakeAttachmentFileStore()
    private val scope = AppCoroutineScope()
    private val clock = MutableClock(1_800_000_000_000L)

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
        accountApi = backend.accountApi(),
        stockApi = backend.stockApi(),
        stockDao = db.stockDao(),
        connectivity = connectivity,
        syncState = SyncStateHolder(),
        scope = scope,
        clock = clock,
        backgroundSync = FakeBackgroundSync(),
        awaitedServerVersions = db.syncIssueActionDao(),
    )

    private val savesWithoutStartingAPass = FakeSyncer()
    private val projects = ProjectRepositoryImpl(db.projectDao(), savesWithoutStartingAPass, scope)
    private val stages = StageRepositoryImpl(db.stageDao(), savesWithoutStartingAPass, scope)
    private val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), savesWithoutStartingAPass, scope)
    private val materials = MaterialRepositoryImpl(db.materialDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.stockDao(), savesWithoutStartingAPass, scope)
    private val purchaseLines = PurchaseLineRepositoryImpl(db.purchaseLineDao(), savesWithoutStartingAPass, scope)
    private val consumptionLines = ConsumptionLineRepositoryImpl(db.consumptionLineDao(), savesWithoutStartingAPass, scope)
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, savesWithoutStartingAPass, scope)
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest fun close() = db.close()

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    private class Removal(val name: String, val row: suspend () -> SyncedRow?)

    private fun stateOf(row: SyncedRow?) = row?.let { listOf(it.syncStatus, it.pendingOp) }

    private val waitingToBeRemoved = listOf<Any>(SyncStatus.PENDING, PendingOp.DELETE)

    @Test
    fun no_pull_brings_back_a_row_whose_removal_is_waiting_to_be_sent() = runTest {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seed(ServerProject(id = 8, name = "Hangar"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedStage(ServerStage(id = 92, projectId = 5, name = "Finitions"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedLog(ServerLog(id = 801, stageId = 90, date = "2026-09-06"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
        backend.seedEntry(ServerEntry(id = 910, dailyLogId = 801, type = "PURCHASE", summary = "À retirer"))
        backend.seedAttachment(ServerAttachment(id = 50, entryId = 900, originalName = "bon.jpg"))
        backend.seedAttachment(ServerAttachment(id = 51, entryId = 900, originalName = "visite.mp4", mimeType = "video/mp4"))
        suspend fun everyPull() {
            assertEquals(SyncOutcome.Synced, engine.syncNow())
            for (project in db.projectDao().findAll()) assertEquals(SyncOutcome.Synced, engine.syncProject(project.localId), "project ${project.name}")
            for (stage in db.stageDao().findForProject(db.projectDao().findByServerId(5)!!.localId)) {
                assertEquals(SyncOutcome.Synced, engine.syncStage(stage.localId), "stage ${stage.name}")
                for (log in db.dailyLogDao().findForStage(stage.localId)) assertEquals(SyncOutcome.Synced, engine.syncLog(log.localId), "day ${log.date}")
            }
        }
        everyPull()
        val villa = db.projectDao().findByServerId(5)!!.localId
        val shed = db.projectDao().findByServerId(8)!!.localId
        val structure = db.stageDao().findForProject(villa).single { it.serverId == 90L }.localId
        val finishing = db.stageDao().findForProject(villa).single { it.serverId == 92L }.localId
        val cement = db.materialDao().findByServerId(7)!!.localId
        val firstDay = db.dailyLogDao().findForStage(structure).single { it.serverId == 800L }.localId
        val secondDay = db.dailyLogDao().findForStage(structure).single { it.serverId == 801L }.localId
        val purchases = db.dailyEntryDao().findForLog(firstDay).single { it.serverId == 900L }.localId
        val works = db.dailyEntryDao().findForLog(firstDay).single { it.serverId == 901L }.localId
        val entryToRemove = db.dailyEntryDao().findForLog(secondDay).single().localId
        val bought = purchaseLines.createLine(purchases, CreatePurchaseLineInput(cement, 9.0, 6.0, null))
        val used = consumptionLines.createLine(works, CreateConsumptionLineInput(cement, 2.0))
        everyPull()
        val photo = db.attachmentDao().findByServerId(50)!!.localId
        val video = db.attachmentDao().findByServerId(51)!!.localId
        val filesKeptBefore = fileStore.storedPaths.size

        projects.deleteProject(shed)
        stages.deleteStage(finishing)
        db.dailyEntryDao().upsert(db.dailyEntryDao().findByLocalId(entryToRemove)!!.copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        purchaseLines.deleteLine(bought)
        consumptionLines.deleteLine(used)
        attachments.deleteAttachment(photo)
        attachments.deleteAttachment(video)
        val removals = listOf(
            Removal("project") { db.projectDao().findByLocalId(shed) },
            Removal("stage") { db.stageDao().findByLocalId(finishing) },
            Removal("entry") { db.dailyEntryDao().findByLocalId(entryToRemove) },
            Removal("purchase line") { db.purchaseLineDao().findByLocalId(bought) },
            Removal("consumption line") { db.consumptionLineDao().findByLocalId(used) },
            Removal("photo") { db.attachmentDao().findByLocalId(photo) },
            Removal("video") { db.attachmentDao().findByLocalId(video) },
        )
        removals.forEach { assertEquals<Any?>(waitingToBeRemoved, stateOf(it.row()), "${it.name}: its removal waits") }
        backend.deleteStatus = HttpStatusCode.ServiceUnavailable
        backend.projects.single { it.id == 8L }.apply { name = "Hangar agrandi"; updatedAt = "2026-10-10T10:00:00" }
        backend.stages.single { it.id == 92L }.name = "Finitions et peinture"
        backend.entries.single { it.id == 910L }.apply { summary = "Modifié ailleurs"; modifiedAt = "2026-10-10T10:00:00" }
        backend.purchaseLines.single().quantity = 12.0
        backend.consumptionLines.single().quantity = 3.0

        repeat(2) {
            everyPull()
            after(24.hours)
        }

        removals.forEach { assertEquals<Any?>(waitingToBeRemoved, stateOf(it.row()), "${it.name}: no pull brought it back") }
        assertEquals(2, db.attachmentDao().findForEntry(purchases).size, "no second row for a file whose removal waits")
        assertEquals(filesKeptBefore - 2, fileStore.storedPaths.size, "and the files freed at removal are not downloaded again")
        assertEquals(listOf(0, 0, 0, 0), listOf(
            projects.observeProjects().first().count { it.localId == shed },
            stages.observeStages(villa).first().count { it.localId == finishing },
            purchaseLines.observeLines(purchases).first().size,
            attachments.observeAttachments(purchases).first().size,
        ), "nothing the user removed is shown again")

        backend.deleteStatus = null
        everyPull()
        removals.forEach { assertEquals(null, it.row(), "${it.name}: removed once the server answers") }
        assertEquals(listOf(listOf(5L), listOf(90L), listOf(900L, 901L), 0, 0, 0), listOf(backend.projects.map { it.id }, backend.stages.map { it.id }, backend.entries.map { it.id }, backend.purchaseLines.size, backend.consumptionLines.size, backend.attachments.size))
    }

    @Test
    fun there_is_no_removal_of_a_material_to_protect() = runTest {
        val writes = com.dmb.chantiertracker.domain.repository.MaterialRepository::class.java.declaredMethods.map { it.name }.filter { it.startsWith("delete") || it.startsWith("remove") }
        assertEquals(emptyList(), writes, "the day a material can be removed, add it to the test above")
    }
}
