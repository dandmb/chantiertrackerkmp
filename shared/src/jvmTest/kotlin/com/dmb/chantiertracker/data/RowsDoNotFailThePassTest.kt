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

class RowsDoNotFailThePassTest {

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

    private class Table(val target: SyncIssueTarget, val create: suspend (failing: Boolean) -> String, val row: suspend (String) -> SyncedRow?)

    private class Change(val name: String, val target: SyncIssueTarget, val localId: String, val request: String, val make: suspend () -> Unit, val row: suspend () -> SyncedRow?)

    private var counter = 0
    private var nextWitness = 6_000L
    private var sendsOfTheFailingRow = 0
    private var answer: ServerRefusal? = null
    private var networkIsCut = false
    private var isTheFailingRow: (HttpRequestData) -> Boolean = ::carriesTheMarkOfAFailingRow

    private fun carriesTheMarkOfAFailingRow(request: HttpRequestData): Boolean {
        if (request.method.value != "POST") return false
        val body = (request.body as? TextContent)?.text.orEmpty()
        return FAILING_NAME in body || "\"quantity\":$FAILING_QUANTITY" in body || FAILING_MONTH in request.url.encodedPath
    }

    private fun theFailingRowIsWatched() {
        backend.cutTheNetworkFor = { request ->
            val concerned = isTheFailingRow(request)
            if (concerned) sendsOfTheFailingRow++
            concerned && networkIsCut
        }
        backend.refuseWith = { request -> if (isTheFailingRow(request)) answer else null }
    }

    private fun theServerAnswers(refusal: ServerRefusal?, cuttingTheNetwork: Boolean = false) {
        answer = refusal
        networkIsCut = cuttingTheNetwork
    }

    private fun theServerAcceptsEverything() = theServerAnswers(null)

    private val tables: List<Table> = listOf(
        Table(SyncIssueTarget.PROJECT, { failing -> projects.createProject(CreateProjectInput(nameOf(failing), null, null, "EUR", "Europe/Paris")) }, { db.projectDao().findByLocalId(it) }),
        Table(SyncIssueTarget.STAGE, { failing -> stages.createStage(CreateStageInput("p5", nameOf(failing), null, null, null, null)) }, { db.stageDao().findByLocalId(it) }),
        Table(SyncIssueTarget.MATERIAL, { failing -> materials.createMaterial("p5", nameOf(failing), "t").localId }, { db.materialDao().findByLocalId(it) }),
        Table(SyncIssueTarget.ENTRY, { failing -> logs.createWorkEntry("st90", dateOf(failing)).entryLocalId }, { db.dailyEntryDao().findByLocalId(it) }),
        Table(SyncIssueTarget.PURCHASE_LINE, { failing -> purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", quantityOf(failing), 6.0, null)) }, { db.purchaseLineDao().findByLocalId(it) }),
        Table(SyncIssueTarget.CONSUMPTION_LINE, { failing -> consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", quantityOf(failing))) }, { db.consumptionLineDao().findByLocalId(it) }),
    )

    private fun nameOf(failing: Boolean) = "${if (failing) FAILING_NAME else "Sain"} ${counter++}"

    private fun dateOf(failing: Boolean) = "${if (failing) FAILING_MONTH else "2026-12-"}${10 + counter++}"

    private fun quantityOf(failing: Boolean) = if (failing) FAILING_QUANTITY else 2.0

    private suspend fun aSyncedSite() {
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
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = none, syncStatus = synced))
        theFailingRowIsWatched()
    }

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    private suspend fun threePasses() = repeat(3) { engine.syncNow() }

    private fun somethingArrivesOnTheServer(): Long = nextWitness++.also { backend.seed(ServerProject(id = it, name = "Arrivé pendant la passe $it")) }

    private suspend fun whatIsToReview() = issues.observeIssues().first().filter { it.issue.kind != SyncIssueKind.BLOCKED_BY_PARENT }

    private fun stateOf(row: SyncedRow?) = row?.let { listOf(it.syncStatus, it.pendingOp, it.lastSyncError, it.serverErrorCode) }

    private val waitingToBeCreated = listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null)

    private suspend fun assertTheRestOfThePassWentOn(outcome: SyncOutcome, table: Table, sibling: String, witness: Long, context: String) {
        assertEquals(SyncOutcome.Synced, outcome, "$context: one row does not fail the pass")
        assertEquals(SyncStatus.SYNCED, table.row(sibling)!!.syncStatus, "$context: the other rows are sent")
        assertTrue(db.projectDao().findByServerId(witness) != null, "$context: the pull is done")
    }

    @Test
    fun a_row_the_server_could_not_answer_keeps_waiting_and_is_spaced_while_the_rest_of_the_pass_goes_on() = runTest {
        aSyncedSite()
        val failures: List<Pair<String, () -> Unit>> = listOf(
            "500" to { theServerAnswers(ServerRefusal(HttpStatusCode.InternalServerError, null)) },
            "503" to { theServerAnswers(ServerRefusal(HttpStatusCode.ServiceUnavailable, null)) },
            "network cut" to { theServerAnswers(null, cuttingTheNetwork = true) },
        )
        for (table in tables) {
            for ((failure, fail) in failures) {
                val context = "${table.target}, $failure"
                val failing = table.create(true)
                val sibling = table.create(false)
                val witness = somethingArrivesOnTheServer()
                fail()
                sendsOfTheFailingRow = 0

                val outcome = engine.syncNow()

                assertTheRestOfThePassWentOn(outcome, table, sibling, witness, context)
                assertEquals(waitingToBeCreated, stateOf(table.row(failing)), "$context: still waiting, no refusal written")
                assertEquals(emptyList(), whatIsToReview().map { it.key }, "$context: nothing to review")
                threePasses()
                assertEquals(1, sendsOfTheFailingRow, "$context: not sent again at every pass")

                after(15.minutes - 1.milliseconds)
                threePasses()
                assertEquals(1, sendsOfTheFailingRow, "$context: not before fifteen minutes")
                after(1.milliseconds)
                assertEquals(SyncOutcome.Synced, engine.syncNow(), context)
                assertEquals(2, sendsOfTheFailingRow, "$context: tried again fifteen minutes later")
                assertEquals(waitingToBeCreated, stateOf(table.row(failing)), context)

                theServerAcceptsEverything()
                after(1.hours - 1.milliseconds)
                threePasses()
                assertEquals(2, sendsOfTheFailingRow, "$context: then one hour later")
                after(1.milliseconds)
                engine.syncNow()
                assertEquals(3, sendsOfTheFailingRow, context)
                assertEquals(SyncStatus.SYNCED, table.row(failing)!!.syncStatus, context)
            }
        }
    }

    @Test
    fun a_row_refused_with_a_status_the_app_does_not_know_becomes_a_frozen_refusal_of_unknown_reason() = runTest {
        aSyncedSite()
        val refusals = listOf(
            ServerRefusal(HttpStatusCode.MethodNotAllowed, null),
            ServerRefusal(HttpStatusCode.UnprocessableEntity, "A_CODE_FROM_A_NEWER_SERVER"),
            ServerRefusal(HttpStatusCode.Gone, null),
        )
        for (table in tables) {
            for (refusal in refusals) {
                val context = "${table.target}, ${refusal.status.value}"
                val failing = table.create(true)
                val sibling = table.create(false)
                val witness = somethingArrivesOnTheServer()
                theServerAnswers(refusal)
                sendsOfTheFailingRow = 0

                val outcome = engine.syncNow()

                assertTheRestOfThePassWentOn(outcome, table, sibling, witness, context)
                assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, PendingOp.CREATE, SyncError.REJECTED, refusal.code), stateOf(table.row(failing)), "$context: written as a refusal")
                val item = whatIsToReview().single { it.key == "${table.target}:$failing" }
                assertEquals(listOf<Any?>(SyncIssueKind.REFUSED, RefusalReason.UNKNOWN), listOf(item.issue.kind, item.issue.reason), "$context: shown as a refusal of unknown reason")
                assertFalse(SyncIssueAction.RETRY in item.actions, "$context: nothing says it could pass later")
                assertTrue(SyncIssueAction.DISCARD in item.actions, context)

                threePasses()
                after(24.hours)
                threePasses()
                assertEquals(1, sendsOfTheFailingRow, "$context: frozen, never sent again by itself")
                issues.discard(item)
            }
        }
    }

    @Test
    fun a_rate_limited_row_waits_for_the_time_the_server_gave_while_the_rest_of_the_pass_goes_on() = runTest {
        aSyncedSite()
        for (table in tables) {
            for ((retryAfter, expected) in listOf(30 to 30.seconds, null to 1.minutes)) {
                val context = "${table.target}, Retry-After $retryAfter"
                val failing = table.create(true)
                val sibling = table.create(false)
                val witness = somethingArrivesOnTheServer()
                theServerAnswers(ServerRefusal(HttpStatusCode.TooManyRequests, "RATE_LIMITED", retryAfterSeconds = retryAfter))
                sendsOfTheFailingRow = 0

                val outcome = engine.syncNow()

                assertTheRestOfThePassWentOn(outcome, table, sibling, witness, context)
                assertEquals(waitingToBeCreated, stateOf(table.row(failing)), "$context: a rate limit is not a refusal")
                assertEquals(emptyList(), whatIsToReview().map { it.key }, "$context: nothing to review")

                theServerAcceptsEverything()
                after(expected - 1.milliseconds)
                threePasses()
                assertEquals(1, sendsOfTheFailingRow, "$context: not before the time the server gave")
                after(1.milliseconds)
                assertEquals(SyncOutcome.Synced, engine.syncNow(), context)
                assertEquals(2, sendsOfTheFailingRow, context)
                assertEquals(SyncStatus.SYNCED, table.row(failing)!!.syncStatus, context)
            }
        }
    }

    @Test
    fun a_rate_limit_on_a_row_is_not_counted_as_a_step_of_the_spacing() = runTest {
        aSyncedSite()
        val failing = tables.first().create(true)
        theServerAnswers(ServerRefusal(HttpStatusCode.TooManyRequests, "RATE_LIMITED", retryAfterSeconds = 3))
        repeat(4) {
            engine.syncNow()
            after(3.seconds)
        }
        assertEquals(4, sendsOfTheFailingRow, "each time the quota is still empty the row waits what the server says, no more")

        theServerAnswers(ServerRefusal(HttpStatusCode.InternalServerError, null))
        engine.syncNow()
        theServerAcceptsEverything()
        after(15.minutes - 1.milliseconds)
        threePasses()
        assertEquals(5, sendsOfTheFailingRow)
        after(1.milliseconds)
        engine.syncNow()

        assertEquals(6, sendsOfTheFailingRow, "a server error after rate limits starts the spacing at fifteen minutes")
        assertEquals(SyncStatus.SYNCED, db.projectDao().findByLocalId(failing)!!.syncStatus)
    }

    @Test
    fun the_children_of_a_row_that_got_no_answer_keep_waiting_and_leave_once_it_has_left() = runTest {
        aSyncedSite()
        val project = projects.createProject(CreateProjectInput("$FAILING_NAME parent", null, null, "EUR", "Europe/Paris"))
        val stage = stages.createStage(CreateStageInput(project, "Fondations", null, null, null, null))
        val material = materials.createMaterial(project, "Gravier", "t").localId
        val entry = logs.createPurchaseEntry("st90", "${FAILING_MONTH}05").entryLocalId
        val line = purchaseLines.createLine(entry, CreatePurchaseLineInput("m7", 4.0, 6.0, null))
        theServerAnswers(ServerRefusal(HttpStatusCode.InternalServerError, null))
        backend.receivedMethods.clear()

        assertEquals(SyncOutcome.Synced, engine.syncNow())
        threePasses()

        assertEquals(2, sendsOfTheFailingRow, "the project and the entry, once each")
        assertEquals(listOf("POST /projects", "POST /stages/90/logs/${FAILING_MONTH}05/purchases"), backend.receivedMethods.filter { it.startsWith("POST") }, "nothing is sent for their children")
        assertEquals(
            List(3) { waitingToBeCreated },
            listOf(stateOf(db.stageDao().findByLocalId(stage)), stateOf(db.materialDao().findByLocalId(material)), stateOf(db.purchaseLineDao().findByLocalId(line))),
            "the children keep waiting, without any refusal",
        )
        assertEquals(emptyList(), whatIsToReview().map { it.key })

        theServerAcceptsEverything()
        after(15.minutes)
        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(
            List(5) { SyncStatus.SYNCED },
            listOf(
                db.projectDao().findByLocalId(project)!!.syncStatus, db.stageDao().findByLocalId(stage)!!.syncStatus, db.materialDao().findByLocalId(material)!!.syncStatus,
                db.dailyEntryDao().findByLocalId(entry)!!.syncStatus, db.purchaseLineDao().findByLocalId(line)!!.syncStatus,
            ),
            "the parents left, their children followed in the same pass",
        )
    }

    @Test
    fun an_expired_session_still_fails_the_pass_and_writes_nothing_on_the_row() = runTest {
        aSyncedSite()
        val failing = tables.first().create(true)
        val sibling = tables.first().create(false)
        theServerAnswers(ServerRefusal(HttpStatusCode.Unauthorized, null))

        val outcome = engine.syncNow()

        assertEquals(SyncOutcome.Failed(DomainException.InvalidCredentials), outcome, "a 401 is about the session, not about the row")
        assertEquals(waitingToBeCreated, stateOf(db.projectDao().findByLocalId(failing)))
        assertEquals(waitingToBeCreated, stateOf(db.projectDao().findByLocalId(sibling)), "the pass stopped there")
        assertEquals(emptyList(), whatIsToReview().map { it.key })

        engine.syncNow()
        assertEquals(2, sendsOfTheFailingRow, "and the row is not spaced: it leaves as soon as the session is back")
    }

    @Test
    fun an_update_or_a_delete_that_fails_is_handled_like_a_creation_that_fails() = runTest {
        aSyncedSite()
        backend.seed(ServerProject(id = 8, name = "Annexe"))
        backend.seed(ServerProject(id = 9, name = "Hangar"))
        backend.seedStage(ServerStage(id = 91, projectId = 5, name = "Second œuvre"))
        backend.seedStage(ServerStage(id = 92, projectId = 5, name = "Finitions"))
        backend.seedMaterial(ServerMaterial(id = 8, projectId = 5, name = "Sable", unit = "t"))
        val boughtToEdit = purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 3.0, 6.0, null))
        val boughtToRemove = purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 4.0, 6.0, null))
        val usedToEdit = consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 1.0))
        val usedToRemove = consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 1.5))
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))
        val annex = db.projectDao().findByServerId(8)!!.localId
        val shed = db.projectDao().findByServerId(9)!!.localId
        val secondWork = db.stageDao().findForProject("p5").single { it.serverId == 91L }.localId
        val finishing = db.stageDao().findForProject("p5").single { it.serverId == 92L }.localId
        val sand = db.materialDao().findByServerId(8)!!.localId
        suspend fun purchase(id: String) = db.purchaseLineDao().findByLocalId(id)
        suspend fun consumption(id: String) = db.consumptionLineDao().findByLocalId(id)

        val changes = listOf(
            Change("project update", SyncIssueTarget.PROJECT, annex, "PATCH /projects/8", { projects.updateProject(annex, UpdateProjectInput("Annexe nord", null, null, "USD", "UTC", ProjectStatus.IN_PROGRESS)) }, { db.projectDao().findByLocalId(annex) }),
            Change("project delete", SyncIssueTarget.PROJECT, shed, "DELETE /projects/9", { projects.deleteProject(shed) }, { db.projectDao().findByLocalId(shed) }),
            Change("stage update", SyncIssueTarget.STAGE, secondWork, "PATCH /stages/91", { stages.updateStage(secondWork, UpdateStageInput("Second œuvre bis", null, null, null, null, StageStatus.IN_PROGRESS)) }, { db.stageDao().findByLocalId(secondWork) }),
            Change("stage delete", SyncIssueTarget.STAGE, finishing, "DELETE /stages/92", { stages.deleteStage(finishing) }, { db.stageDao().findByLocalId(finishing) }),
            Change(
                "material update", SyncIssueTarget.MATERIAL, sand, "PATCH /materials/8",
                { db.materialDao().upsert(db.materialDao().findByLocalId(sand)!!.copy(name = "Sable fin", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING)) },
                { db.materialDao().findByLocalId(sand) },
            ),
            Change("entry update", SyncIssueTarget.ENTRY, "e901", "PATCH /entries/901", { logs.updateEntry("e901", "Coulage de la dalle") }, { db.dailyEntryDao().findByLocalId("e901") }),
            Change("purchase line update", SyncIssueTarget.PURCHASE_LINE, boughtToEdit, "PATCH /purchase-lines/${purchase(boughtToEdit)!!.serverId}", { purchaseLines.updateLine(boughtToEdit, UpdatePurchaseLineInput(5.0, 6.0, null)) }, { purchase(boughtToEdit) }),
            Change("purchase line delete", SyncIssueTarget.PURCHASE_LINE, boughtToRemove, "DELETE /purchase-lines/${purchase(boughtToRemove)!!.serverId}", { purchaseLines.deleteLine(boughtToRemove) }, { purchase(boughtToRemove) }),
            Change("consumption line update", SyncIssueTarget.CONSUMPTION_LINE, usedToEdit, "PATCH /consumption-lines/${consumption(usedToEdit)!!.serverId}", { consumptionLines.updateLine(usedToEdit, UpdateConsumptionLineInput(2.0)) }, { consumption(usedToEdit) }),
            Change("consumption line delete", SyncIssueTarget.CONSUMPTION_LINE, usedToRemove, "DELETE /consumption-lines/${consumption(usedToRemove)!!.serverId}", { consumptionLines.deleteLine(usedToRemove) }, { consumption(usedToRemove) }),
        )
        for (change in changes) {
            val isADelete = change.request.startsWith("DELETE")
            isTheFailingRow = { request -> "${request.method.value} ${request.url.encodedPath.removePrefix("/api/v1")}" == change.request }
            change.make()
            val waiting = listOf<Any?>(SyncStatus.PENDING, if (isADelete) PendingOp.DELETE else PendingOp.UPDATE, null, null)
            sendsOfTheFailingRow = 0

            theServerAnswers(ServerRefusal(HttpStatusCode.TooManyRequests, "RATE_LIMITED", retryAfterSeconds = 30))
            var witness = somethingArrivesOnTheServer()
            assertEquals(SyncOutcome.Synced, engine.syncNow(), "${change.name}, 429: one row does not fail the pass")
            assertTrue(db.projectDao().findByServerId(witness) != null, "${change.name}, 429: the pull is done")
            threePasses()
            assertEquals(1, sendsOfTheFailingRow, "${change.name}, 429: waits for the time the server gave")
            assertEquals(waiting, stateOf(change.row()), "${change.name}, 429: not a refusal")

            after(30.seconds)
            theServerAnswers(ServerRefusal(HttpStatusCode.BadGateway, null))
            witness = somethingArrivesOnTheServer()
            assertEquals(SyncOutcome.Synced, engine.syncNow(), "${change.name}, 502: one row does not fail the pass")
            assertTrue(db.projectDao().findByServerId(witness) != null, "${change.name}, 502: the pull is done")
            threePasses()
            assertEquals(2, sendsOfTheFailingRow, "${change.name}, 502: not sent again at every pass")
            assertEquals(waiting, stateOf(change.row()), "${change.name}, 502: not a refusal")
            assertFalse(whatIsToReview().any { it.key == "${change.target}:${change.localId}" }, "${change.name}, 502: nothing to review")

            after(15.minutes)
            theServerAnswers(ServerRefusal(HttpStatusCode.UnprocessableEntity, null))
            witness = somethingArrivesOnTheServer()
            assertEquals(SyncOutcome.Synced, engine.syncNow(), "${change.name}, 422: one row does not fail the pass")
            assertTrue(db.projectDao().findByServerId(witness) != null, "${change.name}, 422: the pull is done")
            val issue = change.row()!!.syncIssue()
            assertEquals(
                listOf<Any?>(if (isADelete) SyncIssueKind.DELETE_REFUSED else SyncIssueKind.UPDATE_REFUSED, RefusalReason.UNKNOWN),
                listOf(issue?.kind, issue?.reason),
                "${change.name}, 422: a refusal of unknown reason",
            )
            assertTrue(whatIsToReview().any { it.key == "${change.target}:${change.localId}" }, "${change.name}, 422: shown in the entries to review")
            threePasses()
            after(24.hours)
            threePasses()
            assertEquals(3, sendsOfTheFailingRow, "${change.name}, 422: never sent again by itself")
        }
    }

    @Test
    fun a_row_that_left_starts_the_spacing_again_from_fifteen_minutes_the_next_time_it_gets_no_answer() = runTest {
        aSyncedSite()
        val project = projects.createProject(CreateProjectInput("$FAILING_NAME puis parti", null, null, "EUR", "Europe/Paris"))
        theServerAnswers(ServerRefusal(HttpStatusCode.InternalServerError, null))
        engine.syncNow()
        after(15.minutes)
        engine.syncNow()
        theServerAcceptsEverything()
        after(1.hours)
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        val serverId = db.projectDao().findByLocalId(project)!!.serverId
        assertTrue(serverId != null, "it left at its third send")

        isTheFailingRow = { request -> "${request.method.value} ${request.url.encodedPath.removePrefix("/api/v1")}" == "PATCH /projects/$serverId" }
        projects.updateProject(project, UpdateProjectInput("Parti puis modifié", null, null, "EUR", "Europe/Paris", ProjectStatus.IN_PROGRESS))
        theServerAnswers(ServerRefusal(HttpStatusCode.InternalServerError, null))
        sendsOfTheFailingRow = 0
        engine.syncNow()
        theServerAcceptsEverything()
        after(15.minutes - 1.milliseconds)
        threePasses()
        assertEquals(1, sendsOfTheFailingRow)
        after(1.milliseconds)
        engine.syncNow()

        assertEquals(2, sendsOfTheFailingRow, "fifteen minutes again, not six hours: what happened before it left is forgotten")
        assertEquals(SyncStatus.SYNCED, db.projectDao().findByLocalId(project)!!.syncStatus)
    }

    @Test
    fun an_update_whose_first_read_is_refused_with_an_unknown_status_becomes_a_refusal_too() = runTest {
        aSyncedSite()
        backend.seed(ServerProject(id = 8, name = "Annexe"))
        backend.seedStage(ServerStage(id = 91, projectId = 5, name = "Second œuvre"))
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(SyncOutcome.Synced, engine.syncProject("p5"))
        val annex = db.projectDao().findByServerId(8)!!.localId
        val secondWork = db.stageDao().findForProject("p5").single { it.serverId == 91L }.localId
        projects.updateProject(annex, UpdateProjectInput("Annexe nord", null, null, "USD", "UTC", ProjectStatus.IN_PROGRESS))
        stages.updateStage(secondWork, UpdateStageInput("Second œuvre bis", null, null, null, null, StageStatus.IN_PROGRESS))
        isTheFailingRow = { request -> "${request.method.value} ${request.url.encodedPath.removePrefix("/api/v1")}" in setOf("GET /projects/8", "GET /stages/91") }
        theServerAnswers(ServerRefusal(HttpStatusCode.Gone, null))
        val witness = somethingArrivesOnTheServer()

        assertEquals(SyncOutcome.Synced, engine.syncNow(), "one row does not fail the pass")

        assertTrue(db.projectDao().findByServerId(witness) != null, "the pull is done")
        for (row in listOf(db.projectDao().findByLocalId(annex), db.stageDao().findByLocalId(secondWork))) {
            val issue = row!!.syncIssue()
            assertEquals(listOf<Any?>(SyncIssueKind.UPDATE_REFUSED, RefusalReason.UNKNOWN), listOf(issue?.kind, issue?.reason))
        }
        threePasses()
        assertEquals(2, sendsOfTheFailingRow, "each read once, never again by itself")
        assertEquals(0, backend.receivedMethods.count { it.startsWith("PATCH") }, "nothing was written on the server")
    }

    private companion object {
        const val FAILING_NAME = "Panne"
        const val FAILING_MONTH = "2026-11-"
        const val FAILING_QUANTITY = 13.5
    }
}
