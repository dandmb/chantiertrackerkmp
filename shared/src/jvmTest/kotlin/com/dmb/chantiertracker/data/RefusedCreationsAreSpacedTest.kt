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
import com.dmb.chantiertracker.data.session.RoomUnsyncedWriteCounter
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.actions
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkers
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerConsumptionLine
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerMember
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerPurchaseLine
import com.dmb.chantiertracker.support.ServerRefusal
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class RefusedCreationsAreSpacedTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val fileStore = FakeAttachmentFileStore()
    private val scope = AppCoroutineScope()
    private val clock = MutableClock(1_800_000_000_000L)

    private fun anEngine() = SyncEngine(
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

    private val engine = anEngine()
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
    private val unsent = RoomUnsyncedWriteCounter(db.localDataDao())

    @AfterTest fun close() = db.close()

    private suspend fun twoSyncedSites(projectStatus: String = "IN_PROGRESS", stageStatus: String = "IN_PROGRESS") {
        backend.nextLogId = 2_000L
        backend.nextEntryId = 3_000L
        backend.seed(ServerProject(id = 5, name = "Villa", status = projectStatus))
        backend.seed(ServerProject(id = 6, name = "Immeuble", status = projectStatus))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre", status = stageStatus))
        backend.seedStage(ServerStage(id = 92, projectId = 5, name = "Toiture", status = stageStatus))
        backend.seedStage(ServerStage(id = 91, projectId = 6, name = "Fondations", status = stageStatus))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedLog(ServerLog(id = 802, stageId = 90, date = "2026-09-06"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
        backend.seedEntry(ServerEntry(id = 902, dailyLogId = 802, type = "WORK", summary = "Dalle"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        backend.seedMaterial(ServerMaterial(id = 8, projectId = 5, name = "Sable", unit = "t"))
        backend.seedPurchaseLine(ServerPurchaseLine(id = 5000, entryId = 900, materialId = 7, quantity = 100.0, unitPrice = 6.0))
        backend.seedPurchaseLine(ServerPurchaseLine(id = 5001, entryId = 900, materialId = 8, quantity = 100.0, unitPrice = 6.0))
        backend.seedConsumptionLine(ServerConsumptionLine(id = 6000, entryId = 901, materialId = 7, quantity = 10.0))
        val synced = SyncStatus.SYNCED
        val none = PendingOp.NONE
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced).copy(status = projectStatus))
        db.projectDao().upsert(localProject("p6", name = "Immeuble", serverId = 6, pendingOp = none, syncStatus = synced).copy(status = projectStatus))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, status = stageStatus, pendingOp = none, syncStatus = synced))
        db.stageDao().upsert(localStage("st92", projectLocalId = "p5", name = "Toiture", serverId = 92, status = stageStatus, pendingOp = none, syncStatus = synced))
        db.stageDao().upsert(localStage("st91", projectLocalId = "p6", name = "Fondations", serverId = 91, status = stageStatus, pendingOp = none, syncStatus = synced))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyLogDao().upsert(localDailyLog("l802", stageLocalId = "st90", date = "2026-09-06", serverId = 802))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e902", dailyLogLocalId = "l802", type = "WORK", summary = "Dalle", serverId = 902, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m8", projectLocalId = "p5", name = "Sable", unit = "t", serverId = 8, pendingOp = none, syncStatus = synced))
        db.purchaseLineDao().upsert(localPurchaseLine("pl5000", entryLocalId = "e900", materialLocalId = "m7", quantity = 100.0, unitPrice = 6.0, serverId = 5000, pendingOp = none, syncStatus = synced))
        db.purchaseLineDao().upsert(localPurchaseLine("pl5001", entryLocalId = "e900", materialLocalId = "m8", quantity = 100.0, unitPrice = 6.0, serverId = 5001, pendingOp = none, syncStatus = synced))
        db.consumptionLineDao().upsert(localConsumptionLine("cl6000", entryLocalId = "e901", materialLocalId = "m7", quantity = 10.0, serverId = 6000, pendingOp = none, syncStatus = synced))
    }

    private class Refusal(val reason: RefusalReason, val code: String?, val status: HttpStatusCode, val withFieldErrors: Boolean = false, val detail: String = "Refusé.") {
        override fun toString() = "$reason ($code, ${status.value})"
    }

    private val invalid = Refusal(RefusalReason.INVALID_VALUE, "VALIDATION_FAILED", HttpStatusCode.BadRequest, withFieldErrors = true)
    private val inactive = Refusal(RefusalReason.PROJECT_OR_STAGE_INACTIVE, "PROJECT_OR_STAGE_INACTIVE", HttpStatusCode.Forbidden)
    private val notAllowed = Refusal(RefusalReason.INSUFFICIENT_ROLE, "PROJECT_INSUFFICIENT_ROLE", HttpStatusCode.Forbidden)
    private val pastDay = Refusal(RefusalReason.ENTRY_DATE_RESTRICTED, "ENTRY_DATE_RESTRICTED", HttpStatusCode.Forbidden)
    private val planLimit = Refusal(RefusalReason.PLAN_LIMIT, "PLAN_LIMIT_EXCEEDED", HttpStatusCode.Forbidden, detail = "Vous avez atteint la limite de projets de votre formule.")
    private val duplicateEntry = Refusal(RefusalReason.DUPLICATE_ENTRY, "DUPLICATE_ENTRY", HttpStatusCode.Conflict)
    private val duplicateMaterial = Refusal(RefusalReason.DUPLICATE_MATERIAL, "DUPLICATE_MATERIAL", HttpStatusCode.Conflict)
    private val invalidAmount = Refusal(RefusalReason.INVALID_VALUE, "INVALID_AMOUNT", HttpStatusCode.BadRequest)
    private val notEnoughStock = Refusal(RefusalReason.INSUFFICIENT_STOCK, "INSUFFICIENT_STOCK", HttpStatusCode.Conflict)
    private val serverDown = Refusal(RefusalReason.UNKNOWN, null, HttpStatusCode.InternalServerError)

    private inner class Table(
        val name: String,
        val target: SyncIssueTarget,
        val createPath: () -> String,
        val create: suspend () -> String,
        val row: suspend (String) -> SyncedRow?,
        val frozen: List<Refusal>,
        val spaced: List<Refusal>,
    )

    private var counter = 0
    private var entryDay = "2026-10-01"

    private val tables = listOf(
        Table(
            "project", SyncIssueTarget.PROJECT, { "/projects" },
            create = { projects.createProject(CreateProjectInput("Chantier ${counter++}", null, null, "EUR", "Europe/Paris")) },
            row = { db.projectDao().findByLocalId(it) }, frozen = listOf(invalid), spaced = listOf(planLimit),
        ),
        Table(
            "stage", SyncIssueTarget.STAGE, { "/projects/5/stages" },
            create = { stages.createStage(CreateStageInput("p5", "Étape ${counter++}", null, null, null, null)) },
            row = { db.stageDao().findByLocalId(it) }, frozen = listOf(invalid), spaced = listOf(inactive, notAllowed),
        ),
        Table(
            "material", SyncIssueTarget.MATERIAL, { "/projects/5/materials" },
            create = { materials.createMaterial("p5", "Matériau ${counter++}", "kg").localId },
            row = { db.materialDao().findByLocalId(it) }, frozen = listOf(duplicateMaterial, invalidAmount), spaced = listOf(inactive, notAllowed),
        ),
        Table(
            "entry", SyncIssueTarget.ENTRY, { "/stages/90/logs/$entryDay/works" },
            create = {
                entryDay = "2026-10-" + (10 + counter++).toString()
                logs.createWorkEntry("st90", entryDay).entryLocalId
            },
            row = { db.dailyEntryDao().findByLocalId(it) }, frozen = listOf(duplicateEntry, pastDay), spaced = listOf(inactive, notAllowed),
        ),
        Table(
            "purchase line", SyncIssueTarget.PURCHASE_LINE, { "/entries/900/purchase-lines" },
            create = { purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 5.0, 6.0, null)) },
            row = { db.purchaseLineDao().findByLocalId(it) }, frozen = listOf(invalidAmount, pastDay), spaced = listOf(inactive, notAllowed),
        ),
        Table(
            "consumption line", SyncIssueTarget.CONSUMPTION_LINE, { "/entries/901/consumption-lines" },
            create = { consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 5.0)) },
            row = { db.consumptionLineDao().findByLocalId(it) }, frozen = listOf(invalidAmount, pastDay), spaced = listOf(notEnoughStock, inactive, notAllowed),
        ),
    )

    private val refusedPosts = mutableMapOf<String, Refusal>()

    private fun theServerRefuses(path: String, refusal: Refusal) {
        refusedPosts[path] = refusal
        backend.refuseWith = { request ->
            refusedPosts.entries.firstOrNull { request.method.value == "POST" && request.url.encodedPath.endsWith(it.key) }?.value
                ?.let { ServerRefusal(it.status, it.code, it.withFieldErrors, it.detail) }
        }
    }

    private fun theServerAccepts(path: String) {
        refusedPosts.remove(path)
    }

    private fun posts(path: String) = backend.receivedMethods.count { it == "POST $path" }

    private fun writes() = backend.receivedMethods.filter { !it.startsWith("GET ") }

    private fun planReads() = backend.receivedMethods.count { it == "GET /users/me/plan-usage" }

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    private suspend fun threePasses() = repeat(3) { engine.syncNow() }

    private suspend fun listed(target: SyncIssueTarget, id: String) = issues.observeIssues().first().singleOrNull { it.target == target && it.localId == id }

    private suspend fun aStageRefusedOn(projectLocalId: String, refusal: Refusal): Pair<String, String> {
        val id = stages.createStage(CreateStageInput(projectLocalId, "Étape ${counter++}", null, null, null, null))
        val path = "/projects/${projectLocalId.removePrefix("p")}/stages"
        theServerRefuses(path, refusal)
        return id to path
    }

    private suspend fun aWorkEntryRefusedOn(stageLocalId: String, day: String, refusal: Refusal): Pair<String, String> {
        val id = logs.createWorkEntry(stageLocalId, day).entryLocalId
        val path = "/stages/${stageLocalId.removePrefix("st")}/logs/$day/works"
        theServerRefuses(path, refusal)
        return id to path
    }

    private suspend fun aConsumptionRefusedOn(entryLocalId: String, materialLocalId: String, refusal: Refusal = notEnoughStock): Pair<String, String> {
        val id = consumptionLines.createLine(entryLocalId, CreateConsumptionLineInput(materialLocalId, 500.0))
        val path = "/entries/${entryLocalId.removePrefix("e")}/consumption-lines"
        theServerRefuses(path, refusal)
        return id to path
    }

    @Test
    fun a_creation_refused_for_a_reason_that_can_lift_is_not_sent_again_before_its_time_and_is_sent_once_when_it_comes() = runTest {
        twoSyncedSites()
        for (table in tables) {
            for (refusal in table.spaced) {
                val id = table.create()
                val path = table.createPath()
                val before = posts(path)
                val context = "${table.name}, $refusal"
                theServerRefuses(path, refusal)

                threePasses()
                assertEquals(1, posts(path) - before, "$context: three passes right after the refusal, one request")

                after(15.minutes - 1.milliseconds)
                threePasses()
                assertEquals(1, posts(path) - before, "$context: one millisecond before its time, still one request")

                after(1.milliseconds)
                threePasses()
                assertEquals(2, posts(path) - before, "$context: its time has come, one more request and only one")
                val row = table.row(id)!!
                assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, PendingOp.CREATE, refusal.code), listOf(row.syncStatus, row.pendingOp, row.serverErrorCode), context)
                assertEquals(refusal.reason, listed(table.target, id)?.issue?.reason, "$context: still listed to review")

                theServerAccepts(path)
                threePasses()
                assertEquals(2, posts(path) - before, "$context: the cause is lifted but nothing tells the app, it waits for its time")

                after(1.hours)
                assertEquals(SyncOutcome.Synced, engine.syncNow(), context)
                assertEquals(3, posts(path) - before, context)
                assertEquals(SyncStatus.SYNCED, table.row(id)!!.syncStatus, "$context: accepted at its time, without any action")
                assertEquals(null, listed(table.target, id), context)
            }
        }
    }

    @Test
    fun the_spacing_grows_15_minutes_then_1_hour_then_6_hours_then_once_a_day() = runTest {
        twoSyncedSites()
        val (_, path) = aStageRefusedOn("p5", inactive)
        engine.syncNow()
        var sent = 1

        for (wait in listOf(15.minutes, 1.hours, 6.hours, 24.hours, 24.hours, 24.hours)) {
            after(wait - 1.milliseconds)
            threePasses()
            assertEquals(sent, posts(path), "request ${sent + 1} is not sent before $wait have passed since the last refusal")
            after(1.milliseconds)
            threePasses()
            sent++
            assertEquals(sent, posts(path), "request $sent is sent once $wait have passed")
        }
    }

    @Test
    fun retry_sends_the_entry_now_whatever_its_time_and_the_spacing_goes_on_from_where_it_was() = runTest {
        twoSyncedSites()
        val (stage, path) = aStageRefusedOn("p5", inactive)
        val (other, otherPath) = aStageRefusedOn("p6", inactive)
        threePasses()
        assertEquals(listOf(1, 1), listOf(posts(path), posts(otherPath)))

        assertEquals(RetryOutcome.STILL_REFUSED, issues.retry(listed(SyncIssueTarget.STAGE, stage)!!))

        assertEquals(listOf(2, 1), listOf(posts(path), posts(otherPath)), "the entry asked for is sent at once, the other one keeps waiting")
        after(1.hours - 1.milliseconds)
        engine.syncNow()
        assertEquals(2, posts(path), "refused a second time: it now waits one hour, not fifteen minutes again")
        after(1.milliseconds)
        engine.syncNow()
        assertEquals(3, posts(path))

        theServerAccepts(path)
        assertEquals(RetryOutcome.ACCEPTED, issues.retry(listed(SyncIssueTarget.STAGE, stage)!!))
        assertEquals(4, posts(path))
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stage)!!.syncStatus)
        assertEquals(SyncStatus.CONFLICTED, db.stageDao().findByLocalId(other)!!.syncStatus)
    }

    @Test
    fun a_consumption_refused_for_lack_of_stock_offers_retry_and_retry_sends_it_now() = runTest {
        twoSyncedSites()
        val (line, path) = aConsumptionRefusedOn("e901", "m7")
        threePasses()
        val item = listed(SyncIssueTarget.CONSUMPTION_LINE, line)!!

        assertEquals(listOf(SyncIssueAction.FIX, SyncIssueAction.RETRY, SyncIssueAction.DISCARD), item.actions)
        assertEquals(RetryOutcome.STILL_REFUSED, issues.retry(item))
        assertEquals(2, posts(path))
        theServerAccepts(path)
        assertEquals(RetryOutcome.ACCEPTED, issues.retry(item))
        assertEquals(3, posts(path))
    }

    @Test
    fun a_project_seen_reopened_by_the_pull_sends_what_was_refused_under_it_in_that_same_pass_and_nothing_else() = runTest {
        twoSyncedSites(projectStatus = "SUSPENDED")
        val (stage, stagePath) = aStageRefusedOn("p5", inactive)
        val (entry, entryPath) = aWorkEntryRefusedOn("st92", "2026-10-01", inactive)
        val (otherStage, otherPath) = aStageRefusedOn("p6", inactive)
        val material = materials.createMaterial("p5", "Gravier", "t").localId
        theServerRefuses("/projects/5/materials", notAllowed)
        threePasses()
        assertEquals(listOf(1, 1, 1, 1), listOf(stagePath, entryPath, otherPath, "/projects/5/materials").map { posts(it) })

        backend.projects.single { it.id == 5L }.status = "IN_PROGRESS"
        theServerAccepts(stagePath)
        theServerAccepts(entryPath)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(listOf(2, 2), listOf(posts(stagePath), posts(entryPath)), "sent in the pass whose pull saw the project reopened, well before their time")
        assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.SYNCED), listOf(db.stageDao().findByLocalId(stage)!!.syncStatus, db.dailyEntryDao().findByLocalId(entry)!!.syncStatus))
        assertEquals(1, posts(otherPath), "the other project did not change: its entry keeps waiting")
        assertEquals(SyncStatus.CONFLICTED, db.stageDao().findByLocalId(otherStage)!!.syncStatus)
        assertEquals(1, posts("/projects/5/materials"), "refused for the role, which did not change: it keeps waiting")
        assertEquals(SyncStatus.CONFLICTED, db.materialDao().findByLocalId(material)!!.syncStatus)
    }

    @Test
    fun a_project_seen_reopened_when_it_is_opened_sends_what_was_refused_under_it() = runTest {
        twoSyncedSites(projectStatus = "SUSPENDED")
        val (stage, stagePath) = aStageRefusedOn("p5", inactive)
        val (_, otherPath) = aStageRefusedOn("p6", inactive)
        threePasses()

        backend.projects.forEach { it.status = "IN_PROGRESS" }
        theServerAccepts(stagePath)
        theServerAccepts(otherPath)
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))

        assertEquals(2, posts(stagePath))
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stage)!!.syncStatus)
        assertEquals(1, posts(otherPath), "the other project was reopened too, but this pass did not read it")
    }

    @Test
    fun a_stage_seen_reopened_sends_the_entries_refused_under_it_and_not_those_of_another_stage() = runTest {
        twoSyncedSites(stageStatus = "COMPLETED")
        val (entry, entryPath) = aWorkEntryRefusedOn("st90", "2026-10-01", inactive)
        val (otherEntry, otherPath) = aWorkEntryRefusedOn("st92", "2026-10-01", inactive)
        val (newStage, newStagePath) = aStageRefusedOn("p5", inactive)
        threePasses()

        backend.stages.single { it.id == 90L }.status = "IN_PROGRESS"
        theServerAccepts(entryPath)
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))

        assertEquals(listOf(2, 1, 1), listOf(posts(entryPath), posts(otherPath), posts(newStagePath)), "only the entry of the reopened stage")
        assertEquals(SyncStatus.SYNCED, db.dailyEntryDao().findByLocalId(entry)!!.syncStatus)
        assertEquals(SyncStatus.CONFLICTED, db.stageDao().findByLocalId(newStage)!!.syncStatus)

        backend.stages.single { it.id == 92L }.status = "IN_PROGRESS"
        theServerAccepts(otherPath)
        assertEquals(SyncOutcome.Synced, engine.syncStage("st92"))

        assertEquals(2, posts(otherPath), "seen when the stage itself is opened")
        assertEquals(SyncStatus.SYNCED, db.dailyEntryDao().findByLocalId(otherEntry)!!.syncStatus)
    }

    @Test
    fun a_role_seen_changed_on_a_project_sends_what_a_role_can_lift_on_that_project_only() = runTest {
        twoSyncedSites()
        backend.seedMembers(5, ServerMember(1, "Awa", "awa@site.test", "ADMIN"), ServerMember(2, "Moussa", "moussa@site.test", "SUPERVISOR"))
        backend.seedMembers(6, ServerMember(1, "Awa", "awa@site.test", "ADMIN"), ServerMember(2, "Moussa", "moussa@site.test", "SUPERVISOR"))
        engine.syncProject("p5")
        engine.syncProject("p6")
        val material = materials.createMaterial("p5", "Gravier", "t").localId
        theServerRefuses("/projects/5/materials", notAllowed)
        val otherMaterial = materials.createMaterial("p6", "Gravier", "t").localId
        theServerRefuses("/projects/6/materials", notAllowed)
        val (stage, stagePath) = aStageRefusedOn("p5", inactive)
        val (line, linePath) = aConsumptionRefusedOn("e901", "m7")
        threePasses()
        engine.syncProject("p5")
        engine.syncProject("p6")
        assertEquals(listOf(1, 1, 1, 1), listOf("/projects/5/materials", "/projects/6/materials", stagePath, linePath).map { posts(it) }, "reading the same roles again sends nothing")

        backend.members[5] = mutableListOf(ServerMember(1, "Awa", "awa@site.test", "ADMIN"), ServerMember(2, "Moussa", "moussa@site.test", "ADMIN"))
        theServerAccepts("/projects/5/materials")
        theServerAccepts("/projects/6/materials")
        theServerAccepts(stagePath)
        theServerAccepts(linePath)
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))
        assertEquals(SyncOutcome.Synced, engine.syncProject("p6"))

        assertEquals(2, posts("/projects/5/materials"), "sent in the pass that read the new role")
        assertEquals(SyncStatus.SYNCED, db.materialDao().findByLocalId(material)!!.syncStatus)
        assertEquals(1, posts("/projects/6/materials"), "no role changed on the other project")
        assertEquals(SyncStatus.CONFLICTED, db.materialDao().findByLocalId(otherMaterial)!!.syncStatus)
        assertEquals(2, posts(stagePath), "an administrator writes on an inactive project: what was refused for that leaves too")
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stage)!!.syncStatus)
        assertEquals(1, posts(linePath), "a role brings no stock")
        assertEquals(SyncStatus.CONFLICTED, db.consumptionLineDao().findByLocalId(line)!!.syncStatus)
    }

    @Test
    fun a_status_seen_changed_to_one_that_still_refuses_sends_nothing() = runTest {
        twoSyncedSites()
        val (_, stagePath) = aStageRefusedOn("p5", inactive)
        val (_, entryPath) = aWorkEntryRefusedOn("st92", "2026-10-01", inactive)
        backend.projects.single { it.id == 5L }.status = "SUSPENDED"
        backend.stages.single { it.id == 92L }.status = "COMPLETED"

        engine.syncNow()
        engine.syncProject("p5")
        engine.syncStage("st92")
        assertEquals(listOf("SUSPENDED", "COMPLETED"), listOf(db.projectDao().findByLocalId("p5")!!.status, db.stageDao().findByLocalId("st92")!!.status))
        assertEquals(listOf(1, 1), listOf(posts(stagePath), posts(entryPath)), "the pull that brings the suspension explains the refusal, it does not lift it")

        backend.projects.single { it.id == 5L }.status = "COMPLETED"
        engine.syncNow()
        engine.syncProject("p5")
        assertEquals(listOf(1, 1), listOf(posts(stagePath), posts(entryPath)), "suspended then completed: still closed")
    }

    @Test
    fun a_plan_seen_changed_sends_the_project_refused_for_the_plan_limit_with_what_waits_under_it_and_nothing_else() = runTest {
        twoSyncedSites()
        engine.syncNow()
        assertEquals(0, planReads(), "the plan is not read while no project waits for it")
        val project = projects.createProject(CreateProjectInput("Troisième chantier", null, null, "EUR", "Europe/Paris"))
        theServerRefuses("/projects", planLimit)
        val stageUnder = stages.createStage(CreateStageInput(project, "Sous le projet refusé", null, null, null, null))
        val (stage, stagePath) = aStageRefusedOn("p5", inactive)
        threePasses()
        assertEquals(listOf(1, 1), listOf(posts("/projects"), posts(stagePath)), "the same plan read again sends nothing")

        backend.plan = "SEMI_FLEX"
        backend.planProjectsLimit = 3
        theServerAccepts("/projects")
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(2, posts("/projects"), "sent in the pass that read the new plan")
        assertEquals(SyncStatus.SYNCED, db.projectDao().findByLocalId(project)!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stageUnder)!!.syncStatus, "and its stage follows in the same pass")
        assertEquals(1, posts(stagePath), "a plan change does not concern an entry refused on an inactive project")
        assertEquals(SyncStatus.CONFLICTED, db.stageDao().findByLocalId(stage)!!.syncStatus)
        val readsSoFar = planReads()
        engine.syncNow()
        assertEquals(readsSoFar, planReads(), "no project waits for the plan any more: it is not read")
    }

    @Test
    fun a_project_slot_seen_freed_sends_the_project_refused_for_the_plan_limit() = runTest {
        twoSyncedSites()
        val project = projects.createProject(CreateProjectInput("Troisième chantier", null, null, "EUR", "Europe/Paris"))
        theServerRefuses("/projects", planLimit)
        threePasses()
        assertEquals(1, posts("/projects"))

        backend.projects.removeAll { it.id == 6L }
        theServerAccepts("/projects")
        engine.syncNow()

        assertEquals(2, posts("/projects"))
        assertEquals(SyncStatus.SYNCED, db.projectDao().findByLocalId(project)!!.syncStatus)
    }

    @Test
    fun a_purchase_accepted_for_a_material_sends_the_consumption_refused_for_lack_of_that_material_in_the_same_pass() = runTest {
        twoSyncedSites()
        val (cement, cementPath) = aConsumptionRefusedOn("e901", "m7")
        val (sand, sandPath) = aConsumptionRefusedOn("e902", "m8")
        threePasses()
        assertEquals(listOf(1, 1), listOf(posts(cementPath), posts(sandPath)))

        purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 600.0, 6.0, null))
        theServerAccepts(cementPath)
        theServerAccepts(sandPath)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(2, posts(cementPath), "the purchase and the consumption leave in the same pass")
        assertEquals(SyncStatus.SYNCED, db.consumptionLineDao().findByLocalId(cement)!!.syncStatus)
        assertEquals(1, posts(sandPath), "nothing was bought for the other material")
        assertEquals(SyncStatus.CONFLICTED, db.consumptionLineDao().findByLocalId(sand)!!.syncStatus)
    }

    @Test
    fun a_purchase_raised_and_accepted_sends_the_consumption_refused_for_lack_of_that_material_in_the_same_pass() = runTest {
        twoSyncedSites()
        val (cement, cementPath) = aConsumptionRefusedOn("e901", "m7")
        val (sand, sandPath) = aConsumptionRefusedOn("e902", "m8")
        threePasses()

        purchaseLines.updateLine("pl5000", UpdatePurchaseLineInput(700.0, 6.0, null))
        theServerAccepts(cementPath)
        theServerAccepts(sandPath)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(listOf(2, 1), listOf(posts(cementPath), posts(sandPath)))
        assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.CONFLICTED), listOf(db.consumptionLineDao().findByLocalId(cement)!!.syncStatus, db.consumptionLineDao().findByLocalId(sand)!!.syncStatus))
    }

    @Test
    fun a_stock_seen_risen_on_the_server_sends_the_consumption_refused_for_lack_of_that_material_only() = runTest {
        twoSyncedSites()
        engine.syncProject("p5")
        val (cement, cementPath) = aConsumptionRefusedOn("e901", "m7")
        val (sand, sandPath) = aConsumptionRefusedOn("e902", "m8")
        threePasses()
        engine.syncProject("p5")
        engine.syncLog("l802")
        assertEquals(listOf(1, 1), listOf(posts(cementPath), posts(sandPath)), "the same stock read again sends nothing")

        backend.seedPurchaseLine(ServerPurchaseLine(id = 5002, entryId = 900, materialId = 7, quantity = 600.0, unitPrice = 6.0))
        theServerAccepts(cementPath)
        theServerAccepts(sandPath)
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))

        assertEquals(2, posts(cementPath), "sent in the pass that read the higher stock")
        assertEquals(SyncStatus.SYNCED, db.consumptionLineDao().findByLocalId(cement)!!.syncStatus)
        assertEquals(1, posts(sandPath), "the stock of the other material did not rise")

        backend.seedPurchaseLine(ServerPurchaseLine(id = 5003, entryId = 900, materialId = 8, quantity = 600.0, unitPrice = 6.0))
        assertEquals(SyncOutcome.Synced, engine.syncLog("l802"))

        assertEquals(2, posts(sandPath), "seen as well when a day is opened")
        assertEquals(SyncStatus.SYNCED, db.consumptionLineDao().findByLocalId(sand)!!.syncStatus)
    }

    @Test
    fun a_stock_seen_lower_or_read_for_the_first_time_sends_nothing() = runTest {
        twoSyncedSites()
        val (_, cementPath) = aConsumptionRefusedOn("e901", "m7")
        threePasses()

        engine.syncProject("p5")
        assertEquals(1, posts(cementPath), "first read of this stock: nothing to compare it with")

        backend.seedConsumptionLine(ServerConsumptionLine(id = 6001, entryId = 902, materialId = 7, quantity = 20.0))
        engine.syncProject("p5")
        assertEquals(1, posts(cementPath), "the stock went down")
    }

    @Test
    fun the_spacing_starts_again_from_fifteen_minutes_after_a_correction() = runTest {
        twoSyncedSites()
        val (line, path) = aConsumptionRefusedOn("e901", "m7")
        engine.syncNow()
        after(15.minutes)
        engine.syncNow()
        after(1.hours)
        engine.syncNow()
        assertEquals(3, posts(path), "refused three times: it now waits six hours")

        consumptionLines.updateLine(line, UpdateConsumptionLineInput(400.0))
        threePasses()

        assertEquals(4, posts(path), "the corrected entry is sent at the next pass, once")
        assertEquals(RefusalReason.INSUFFICIENT_STOCK, listed(SyncIssueTarget.CONSUMPTION_LINE, line)?.issue?.reason, "refused again")
        after(15.minutes - 1.milliseconds)
        threePasses()
        assertEquals(4, posts(path))
        after(1.milliseconds)
        threePasses()
        assertEquals(5, posts(path), "fifteen minutes after the corrected entry was refused, not six hours")
    }

    @Test
    fun a_creation_frozen_by_its_reason_is_never_sent_again_whatever_the_time_and_whatever_the_pull_shows() = runTest {
        twoSyncedSites(projectStatus = "SUSPENDED")
        engine.syncProject("p5")
        for (table in tables) {
            for (refusal in table.frozen) {
                val id = table.create()
                val path = table.createPath()
                val before = posts(path)
                val context = "${table.name}, $refusal"
                theServerRefuses(path, refusal)
                threePasses()

                after(15.minutes)
                threePasses()
                after(48.hours)
                threePasses()
                backend.projects.forEach { it.status = if (it.status == "SUSPENDED") "IN_PROGRESS" else "SUSPENDED" }
                backend.plan = if (backend.plan == "FREE") "SEMI_FLEX" else "FREE"
                backend.seedPurchaseLine(ServerPurchaseLine(id = backend.nextLineId++, entryId = 900, materialId = 7, quantity = 10.0, unitPrice = 6.0))
                engine.syncNow()
                engine.syncProject("p5")
                engine.syncLog("l800")

                assertEquals(1, posts(path) - before, "$context: one request, ever")
                assertEquals(SyncStatus.CONFLICTED, table.row(id)!!.syncStatus, context)
                assertEquals(false, SyncIssueAction.RETRY in listed(table.target, id)!!.actions, "$context: no Retry")
                theServerAccepts(path)
            }
        }
    }

    @Test
    fun what_was_created_under_a_parent_that_waits_for_its_time_stays_waiting_and_leaves_with_it() = runTest {
        twoSyncedSites()
        val project = projects.createProject(CreateProjectInput("Projet en attente", null, null, "EUR", "Europe/Paris"))
        theServerRefuses("/projects", planLimit)
        val stageUnderProject = stages.createStage(CreateStageInput(project, "Sous le projet", null, null, null, null))
        val materialUnderProject = materials.createMaterial(project, "Sable fin", "t").localId
        val (stage, stagePath) = aStageRefusedOn("p5", inactive)
        val entryUnderStage = logs.createPurchaseEntry(stage, "2026-10-02").entryLocalId
        val lineUnderStage = purchaseLines.createLine(entryUnderStage, CreatePurchaseLineInput("m7", 1.0, 6.0, null))
        val entry = logs.createPurchaseEntry("st90", "2026-10-03").entryLocalId
        theServerRefuses("/stages/90/logs/2026-10-03/purchases", notAllowed)
        val lineUnderEntry = purchaseLines.createLine(entry, CreatePurchaseLineInput("m7", 2.0, 6.0, null))
        val photoUnderEntry = attachments.addAttachment(entry, ByteArray(8), "bon.jpg", "image/jpeg").localId
        val parentPosts = listOf("POST /projects", "POST $stagePath", "POST /stages/90/logs/2026-10-03/purchases")
        suspend fun children() = listOf(
            db.stageDao().findByLocalId(stageUnderProject), db.materialDao().findByLocalId(materialUnderProject), db.dailyEntryDao().findByLocalId(entryUnderStage),
            db.purchaseLineDao().findByLocalId(lineUnderStage), db.purchaseLineDao().findByLocalId(lineUnderEntry), db.attachmentDao().findByLocalId(photoUnderEntry),
        )
        val childIds = listOf(stageUnderProject, materialUnderProject, entryUnderStage, lineUnderStage, lineUnderEntry, photoUnderEntry)

        threePasses()

        assertEquals(parentPosts, writes(), "one request for each parent, none for what waits under them")
        children().forEach { child ->
            assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), listOf(child!!.syncStatus, child.pendingOp, child.lastSyncError, child.serverErrorCode), "$child")
        }
        val shown = issues.observeIssues().first().associate { it.localId to it.issue.kind }
        assertEquals(setOf(SyncIssueKind.BLOCKED_BY_PARENT), childIds.map { shown[it] }.toSet(), "each one is shown as waiting on its refused parent")
        assertEquals(setOf(SyncIssueKind.REFUSED), listOf(project, stage, entry).map { shown[it] }.toSet())

        after(15.minutes)
        threePasses()
        assertEquals(parentPosts + parentPosts, writes(), "when their time comes the parents are sent again, still nothing for the children")

        refusedPosts.clear()
        after(1.hours)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        children().forEach { child -> assertEquals(SyncStatus.SYNCED, child!!.syncStatus, "the parent is accepted and its child leaves in the same pass: $child") }
        assertEquals(emptyList(), issues.observeIssues().first().map { it.key })
    }

    @Test
    fun what_the_user_sees_does_not_depend_on_whether_the_entry_waits_for_its_time() = runTest {
        twoSyncedSites()
        val frozenEntry = logs.createPurchaseEntry("st90", "2026-10-04").entryLocalId
        theServerRefuses("/stages/90/logs/2026-10-04/purchases", pastDay)
        val waitingLine = purchaseLines.createLine(frozenEntry, CreatePurchaseLineInput("m7", 2.0, 6.0, null))
        val (spacedLine, spacedPath) = aConsumptionRefusedOn("e901", "m7")
        suspend fun whatTheUserSees(): List<Any?> {
            val list = issues.observeIssues().first()
            val markers = SyncIssueMarkers(list)
            return listOf(
                list.map { listOf(it.key, it.issue.kind, it.issue.reason, it.actions) }.sortedBy { it.first().toString() },
                issues.observeIssueCount().first(),
                listOf(
                    markers.of(SyncIssueTarget.ENTRY, frozenEntry)?.issue?.kind, markers.of(SyncIssueTarget.PURCHASE_LINE, waitingLine)?.issue?.kind,
                    markers.of(SyncIssueTarget.CONSUMPTION_LINE, spacedLine)?.issue?.kind, markers.of(SyncIssueTarget.ENTRY, "e900")?.issue?.kind,
                ),
                unsent.unsentByKind(),
                unsent.countUnsynced(),
            )
        }

        engine.syncNow()
        val afterTheRefusal = whatTheUserSees()
        threePasses()
        val whileItWaits = whatTheUserSees()
        after(15.minutes)
        threePasses()
        val afterItWasSentAgain = whatTheUserSees()

        assertEquals(listOf(1, 2), listOf(posts("/stages/90/logs/2026-10-04/purchases"), posts(spacedPath)), "the frozen entry once, the other once more when its time came")
        assertEquals(afterTheRefusal, whileItWaits)
        assertEquals(afterTheRefusal, afterItWasSentAgain)
        assertEquals(
            listOf<Any?>(
                listOf(
                    listOf("CONSUMPTION_LINE:$spacedLine", SyncIssueKind.REFUSED, RefusalReason.INSUFFICIENT_STOCK, listOf(SyncIssueAction.FIX, SyncIssueAction.RETRY, SyncIssueAction.DISCARD)),
                    listOf("ENTRY:$frozenEntry", SyncIssueKind.REFUSED, RefusalReason.ENTRY_DATE_RESTRICTED, listOf(SyncIssueAction.DISCARD)),
                    listOf("PURCHASE_LINE:$waitingLine", SyncIssueKind.BLOCKED_BY_PARENT, null, emptyList<SyncIssueAction>()),
                ).sortedBy { it.first().toString() },
                2,
                listOf(SyncIssueKind.REFUSED, SyncIssueKind.BLOCKED_BY_PARENT, SyncIssueKind.REFUSED, null),
                UnsentWrites(entries = 1, lines = 2),
                3,
            ),
            afterTheRefusal,
        )
    }

    @Test
    fun no_read_of_the_server_ever_clears_the_reason_of_a_refusal() = runTest {
        twoSyncedSites()
        for (table in tables) {
            for (refusal in table.frozen + table.spaced) {
                val id = table.create()
                val path = table.createPath()
                val context = "${table.name}, $refusal"
                theServerRefuses(path, refusal)

                engine.syncNow()
                engine.syncNow()
                engine.syncProject("p5")
                engine.syncStage("st90")
                engine.syncLog("l800")

                val row = table.row(id)!!
                assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, PendingOp.CREATE, refusal.code), listOf(row.syncStatus, row.pendingOp, row.serverErrorCode), "$context: the code the server gave is still there")
                assertEquals(refusal.reason, listed(table.target, id)?.issue?.reason, "$context: and so is the reason shown")
                theServerAccepts(path)
                after(48.hours)
                engine.syncNow()
            }
        }
    }

    @Test
    fun a_duplicate_material_with_no_namesake_keeps_its_code_and_reads_as_a_duplicate() = runTest {
        twoSyncedSites()
        val material = materials.createMaterial("p5", "Gravier", "t").localId
        theServerRefuses("/projects/5/materials", duplicateMaterial)

        threePasses()
        engine.syncProject("p5")

        val row = db.materialDao().findByLocalId(material)!!
        assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, "DUPLICATE_MATERIAL"), listOf(row.syncStatus, row.serverErrorCode), "reading the materials to look for a namesake must not lose the code just received")
        assertEquals(RefusalReason.DUPLICATE_MATERIAL, listed(SyncIssueTarget.MATERIAL, material)?.issue?.reason)
        assertEquals(1, posts("/projects/5/materials"))
    }

    @Test
    fun after_the_process_dies_a_waiting_entry_is_sent_once_more_at_most_then_waits_again() = runTest {
        twoSyncedSites()
        val (_, path) = aStageRefusedOn("p5", inactive)
        threePasses()
        assertEquals(1, posts(path))

        val restarted = anEngine()
        repeat(3) { restarted.syncNow() }

        assertEquals(2, posts(path), "the times are kept in memory: one more request after a restart, not one per pass")
        after(15.minutes)
        restarted.syncNow()
        assertEquals(3, posts(path))
    }

    @Test
    fun a_send_that_fails_for_another_reason_than_a_refusal_keeps_its_turn() = runTest {
        twoSyncedSites()
        val (stage, path) = aStageRefusedOn("p5", inactive)
        engine.syncNow()
        after(15.minutes)
        theServerRefuses(path, serverDown)

        assertEquals(SyncOutcome.Synced, engine.syncNow(), "the server is down for this row only: the pass goes on")
        assertEquals(2, posts(path))
        theServerAccepts(path)
        after(15.minutes)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(3, posts(path), "nothing was answered about this entry: it is sent fifteen minutes later, without waiting an hour")
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stage)!!.syncStatus)
    }
}
