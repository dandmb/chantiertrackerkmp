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
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CreationsCarryAnIdempotencyKeyTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val fileStore = FakeAttachmentFileStore()
    private val clock = MutableClock(1_800_000_000_000L)
    private val scope = AppCoroutineScope()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val logged = mutableListOf<String>()

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
        log = { logged += it },
    )

    private val savesWithoutStartingAPass = FakeSyncer()
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest fun close() = db.close()

    private val creationOfEachKind = mapOf(
        "POST /projects" to "p-new",
        "POST /projects/5/stages" to "st-new",
        "POST /projects/5/materials" to "m-new",
        "POST /stages/90/logs/2026-09-06/works" to "e-work",
        "POST /stages/90/logs/2026-09-06/purchases" to "e-purchase",
        "POST /entries/900/purchase-lines" to "pl-new",
        "POST /entries/901/consumption-lines" to "cl-new",
        "POST /entries/901/attachments" to "a-new",
    )

    private suspend fun aSiteWithACreationOfEachKind() {
        backend.nextLogId = 2_000L
        backend.nextEntryId = 3_000L
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        val synced = SyncStatus.SYNCED
        val none = PendingOp.NONE
        val pending = SyncStatus.PENDING
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced))
        db.projectDao().upsert(localProject("p-new", name = "Maison neuve", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
        db.stageDao().upsert(localStage("st-new", projectLocalId = "p5", name = "Toiture", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyLogDao().upsert(localDailyLog("l-new", stageLocalId = "st90", serverId = null, date = "2026-09-06"))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e-work", dailyLogLocalId = "l-new", type = "WORK", summary = "Ferraillage", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.dailyEntryDao().upsert(localDailyEntry("e-purchase", dailyLogLocalId = "l-new", type = "PURCHASE", summary = "Sable", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m-new", projectLocalId = "p5", name = "Sable", unit = "t", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-new", entryLocalId = "e900", materialLocalId = "m7", quantity = 3.0, unitPrice = 6.0))
        db.consumptionLineDao().upsert(localConsumptionLine("cl-new", entryLocalId = "e901", materialLocalId = "m7", quantity = 1.5))
        val photo = fileStore.save(byteArrayOf(4, 5, 6), "fissure.jpg")
        db.attachmentDao().upsert(localAttachment("a-new", entryLocalId = "e901", localPath = photo, originalName = "fissure.jpg", sizeBytes = 3))
    }

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    private fun keysSentFor(request: String) = backend.receivedIdempotencyKeys.filter { it.first == request }.map { it.second }

    private fun aCreation(request: HttpRequestData) = request.method == HttpMethod.Post

    @Test
    fun every_creation_sent_by_the_engine_carries_the_local_id_of_its_row_as_key() = runTest {
        aSiteWithACreationOfEachKind()

        assertEquals(SyncOutcome.Synced, engine.syncNow())

        creationOfEachKind.forEach { (request, localId) ->
            assertEquals(listOf(localId), keysSentFor(request), "$request carries the local id of its row; sent: ${backend.receivedIdempotencyKeys}")
        }
        assertTrue(backend.receivedIdempotencyKeys.none { it.second == null }, "no creation leaves without a key: ${backend.receivedIdempotencyKeys}")
    }

    @Test
    fun a_creation_whose_answer_was_lost_is_resent_with_the_same_key_and_exists_once_on_the_server() = runTest {
        aSiteWithACreationOfEachKind()
        val lostOnce = mutableSetOf<String>()
        backend.loseTheAnswerOf = { request -> aCreation(request) && lostOnce.add(request.url.encodedPath) }

        engine.syncNow()
        engine.syncNowAtTheUsersRequest()
        engine.syncNowAtTheUsersRequest()

        // A photo whose answer was lost is first looked for by its name on the server (D slice 1):
        // found there, it is not sent again. Every other creation is resent and replayed.
        creationOfEachKind.forEach { (request, localId) ->
            val keys = keysSentFor(request)
            if (!request.endsWith("/attachments")) assertTrue(keys.size >= 2, "$request was resent after its answer was lost: $keys")
            assertEquals(setOf(localId), keys.toSet(), "$request: the same key at every resend")
        }
        assertEquals(creationOfEachKind.size - 1, backend.replayedCreations, "each resend is answered by the replay of the first creation")
        assertEquals(1, backend.stages.filter { it.projectId == 5L }.count { it.name == "Toiture" }, "one stage on the server")
        assertEquals(1, backend.materials.filter { it.projectId == 5L }.count { it.name == "Sable" }, "one material on the server")
        assertEquals(1, backend.attachments.filter { it.entryId == 901L }.count { it.originalName == "fissure.jpg" }, "one photo on the server")
        assertEquals(1, backend.purchaseLines.filter { it.entryId == 900L }.count { it.quantity == 3.0 }, "one purchase line on the server")
        assertEquals(1, backend.consumptionLines.filter { it.entryId == 901L }.count { it.quantity == 1.5 }, "one consumption line on the server")
        listOf(
            db.projectDao().findByLocalId("p-new"), db.stageDao().findByLocalId("st-new"), db.materialDao().findByLocalId("m-new"),
            db.dailyEntryDao().findByLocalId("e-work"), db.dailyEntryDao().findByLocalId("e-purchase"), db.purchaseLineDao().findByLocalId("pl-new"),
            db.consumptionLineDao().findByLocalId("cl-new"), db.attachmentDao().findByLocalId("a-new"),
        ).forEach { assertEquals(SyncStatus.SYNCED, it?.syncStatus, "$it") }
    }

    @Test
    fun a_creation_still_in_progress_on_the_server_waits_retry_after_and_is_never_a_refusal() = runTest {
        aSiteWithACreationOfEachKind()
        var inProgress = true
        backend.idempotencyInProgressFor = { inProgress && it.url.encodedPath.endsWith("/projects/5/stages") }

        engine.syncNow()
        engine.syncNowAtTheUsersRequest()
        after(9.seconds)
        engine.syncNow()

        val stage = db.stageDao().findByLocalId("st-new")!!
        assertEquals(listOf(SyncStatus.PENDING, PendingOp.CREATE), listOf(stage.syncStatus, stage.pendingOp), "still waiting, not refused")
        assertEquals(emptyList(), issues.observeIssues().first().filter { it.localId == "st-new" }, "nothing to review")
        assertEquals(1, keysSentFor("POST /projects/5/stages").size, "not resent before Retry-After, even by a manual refresh")
        assertTrue(db.materialDao().findByLocalId("m-new")?.syncStatus == SyncStatus.SYNCED, "the rest of the pass went on")

        inProgress = false
        after(2.seconds)
        engine.syncNow()

        assertEquals(listOf("st-new", "st-new"), keysSentFor("POST /projects/5/stages"))
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId("st-new")?.syncStatus)
    }

    @Test
    fun a_photo_still_in_progress_on_the_server_waits_retry_after_and_is_never_a_refusal() = runTest {
        aSiteWithACreationOfEachKind()
        var inProgress = true
        backend.idempotencyInProgressFor = { inProgress && it.url.encodedPath.endsWith("/entries/901/attachments") }

        engine.syncNow()
        engine.syncNowAtTheUsersRequest()
        after(9.seconds)
        engine.syncNow()

        val photo = db.attachmentDao().findByLocalId("a-new")!!
        assertEquals(listOf(SyncStatus.PENDING, PendingOp.CREATE), listOf(photo.syncStatus, photo.pendingOp), "still waiting, not refused")
        assertEquals(1, keysSentFor("POST /entries/901/attachments").size, "not resent before Retry-After")

        inProgress = false
        after(2.seconds)
        engine.syncNow()

        assertEquals(listOf("a-new", "a-new"), keysSentFor("POST /entries/901/attachments"))
        assertEquals(SyncStatus.SYNCED, db.attachmentDao().findByLocalId("a-new")?.syncStatus)
        assertEquals(1, backend.attachments.filter { it.entryId == 901L }.count { it.originalName == "fissure.jpg" })
    }

    @Test
    fun a_key_reused_for_another_request_is_logged_and_refused_explicitly() = runTest {
        aSiteWithACreationOfEachKind()
        val stages = StageRepositoryImpl(db.stageDao(), savesWithoutStartingAPass, scope)
        var lost = true
        backend.loseTheAnswerOf = { request -> lost && request.url.encodedPath.endsWith("/projects/5/stages") }
        engine.syncNow()
        lost = false
        stages.updateStage("st-new", UpdateStageInput("Toiture et zinguerie", null, null, null, null, StageStatus.IN_PROGRESS))

        engine.syncNowAtTheUsersRequest()
        after(1.seconds)
        engine.syncNowAtTheUsersRequest()

        val stage = db.stageDao().findByLocalId("st-new")!!
        assertEquals(SyncStatus.CONFLICTED, stage.syncStatus, "refused, not resent forever")
        assertEquals("IDEMPOTENCY_KEY_REUSED", stage.serverErrorCode)
        assertEquals(2, keysSentFor("POST /projects/5/stages").size, "sent, resent once, then frozen")
        assertEquals(1, backend.stages.filter { it.projectId == 5L }.count { it.name.startsWith("Toiture") }, "never a second stage")
        assertTrue(logged.any { "IDEMPOTENCY_KEY_REUSED" in it }, "logged: $logged")
        val listed = issues.observeIssues().first().single { it.localId == "st-new" }
        assertEquals(RefusalReason.UNKNOWN, listed.issue.reason, "shown as a refusal, without the server code")
    }

    @Test
    fun the_local_ids_made_by_every_repository_are_valid_keys() = runTest {
        aSiteWithACreationOfEachKind()
        val valid = Regex("^[A-Za-z0-9._:-]{1,128}$")
        val ids = listOf(
            ProjectRepositoryImpl(db.projectDao(), savesWithoutStartingAPass, scope).createProject(CreateProjectInput("Neuf", null, null, "EUR", "Europe/Paris")),
            StageRepositoryImpl(db.stageDao(), savesWithoutStartingAPass, scope).createStage(CreateStageInput("p5", "Neuve", null, null, null, null)),
            MaterialRepositoryImpl(db.materialDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.stockDao(), savesWithoutStartingAPass, scope).createMaterial("p5", "Gravier", "t").localId,
            DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), savesWithoutStartingAPass, scope).createWorkEntry("st90", "2026-09-07").entryLocalId,
            PurchaseLineRepositoryImpl(db.purchaseLineDao(), savesWithoutStartingAPass, scope).createLine("e900", CreatePurchaseLineInput("m7", 1.0, 1.0, null)),
            ConsumptionLineRepositoryImpl(db.consumptionLineDao(), savesWithoutStartingAPass, scope).createLine("e901", CreateConsumptionLineInput("m7", 1.0)),
            AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, savesWithoutStartingAPass, scope)
                .addAttachment("e901", byteArrayOf(1), "dalle.jpg", "image/jpeg").localId,
        )
        ids.forEach { assertTrue(valid.matches(it), "« $it » is a valid Idempotency-Key") }
    }

    @Test
    fun a_local_id_that_is_not_a_valid_key_leaves_without_a_key_rather_than_being_refused() = runTest {
        aSiteWithACreationOfEachKind()
        db.stageDao().upsert(localStage("étape n°1", projectLocalId = "p5", name = "Ancienne", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = SyncStatus.PENDING))

        engine.syncNow()

        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId("étape n°1")?.syncStatus, "never a 400 INVALID_IDEMPOTENCY_KEY")
        assertTrue(backend.receivedIdempotencyKeys.none { it.second == "étape n°1" })
    }
}
