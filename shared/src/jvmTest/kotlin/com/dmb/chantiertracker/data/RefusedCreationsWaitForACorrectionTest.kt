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
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.data.sync.parseServerTimestampMillis
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkers
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
import kotlin.test.assertTrue

class RefusedCreationsWaitForACorrectionTest {

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

    private class Refusal(
        val reason: RefusalReason,
        val code: String?,
        val status: HttpStatusCode,
        val withFieldErrors: Boolean = false,
        val detail: String = "Refusé.",
        val keptCode: String? = code,
        val shownReason: RefusalReason = reason,
    ) {
        override fun toString() = "$reason ($code, ${status.value})"
    }

    private val invalid = Refusal(RefusalReason.INVALID_VALUE, "VALIDATION_FAILED", HttpStatusCode.BadRequest, withFieldErrors = true)
    private val unknownCode = Refusal(RefusalReason.UNKNOWN, "A_CODE_FROM_A_NEWER_SERVER", HttpStatusCode.BadRequest, withFieldErrors = true)
    private val noCode = Refusal(RefusalReason.UNKNOWN, null, HttpStatusCode.BadRequest, withFieldErrors = true)
    private val inactive = Refusal(RefusalReason.PROJECT_OR_STAGE_INACTIVE, "PROJECT_OR_STAGE_INACTIVE", HttpStatusCode.Forbidden)
    private val notAllowed = Refusal(RefusalReason.INSUFFICIENT_ROLE, "PROJECT_INSUFFICIENT_ROLE", HttpStatusCode.Forbidden)
    private val pastDay = Refusal(RefusalReason.ENTRY_DATE_RESTRICTED, "ENTRY_DATE_RESTRICTED", HttpStatusCode.Forbidden)
    private val planLimit = Refusal(RefusalReason.PLAN_LIMIT, "PLAN_LIMIT_EXCEEDED", HttpStatusCode.Forbidden, detail = "Vous avez atteint la limite de projets de votre formule.")
    private val duplicateEntry = Refusal(RefusalReason.DUPLICATE_ENTRY, "DUPLICATE_ENTRY", HttpStatusCode.Conflict)
    private val duplicateMaterial = Refusal(
        RefusalReason.DUPLICATE_MATERIAL, "DUPLICATE_MATERIAL", HttpStatusCode.Conflict,
        keptCode = null, shownReason = RefusalReason.UNKNOWN,
    )
    private val invalidAmount = Refusal(RefusalReason.INVALID_VALUE, "INVALID_AMOUNT", HttpStatusCode.BadRequest)
    private val wrongMaterial = Refusal(RefusalReason.INVALID_VALUE, "MATERIAL_PROJECT_MISMATCH", HttpStatusCode.BadRequest)
    private val wrongEntryType = Refusal(RefusalReason.INVALID_VALUE, "ENTRY_TYPE_MISMATCH", HttpStatusCode.BadRequest)
    private val notEnoughStock = Refusal(RefusalReason.INSUFFICIENT_STOCK, "INSUFFICIENT_STOCK", HttpStatusCode.Conflict)

    private inner class Table(
        val name: String,
        val target: SyncIssueTarget,
        val createPath: () -> String,
        val create: suspend () -> String,
        val correct: (suspend (String) -> Unit)?,
        val row: suspend (String) -> SyncedRow?,
        val remove: suspend (String) -> Unit,
        val frozen: List<Refusal>,
        val sentAgain: List<Refusal>,
    )

    private var counter = 0
    private var entryDay = "2026-10-01"

    private val tables = listOf(
        Table(
            "project", SyncIssueTarget.PROJECT, { "/projects" },
            create = { projects.createProject(CreateProjectInput("Chantier ${counter++}", null, null, "EUR", "Europe/Paris")) },
            correct = { projects.updateProject(it, UpdateProjectInput("Chantier corrigé ${counter++}", null, null, "EUR", "Europe/Paris", ProjectStatus.IN_PROGRESS)) },
            row = { db.projectDao().findByLocalId(it) }, remove = { db.projectDao().deleteByLocalId(it) },
            frozen = listOf(invalid, unknownCode, noCode), sentAgain = listOf(planLimit),
        ),
        Table(
            "stage", SyncIssueTarget.STAGE, { "/projects/5/stages" },
            create = { stages.createStage(CreateStageInput("p5", "Étape ${counter++}", null, null, null, null)) },
            correct = { stages.updateStage(it, UpdateStageInput("Étape corrigée ${counter++}", null, null, null, null, StageStatus.IN_PROGRESS)) },
            row = { db.stageDao().findByLocalId(it) }, remove = { db.stageDao().deleteByLocalId(it) },
            frozen = listOf(invalid, unknownCode, noCode), sentAgain = listOf(inactive, notAllowed),
        ),
        Table(
            "material", SyncIssueTarget.MATERIAL, { "/projects/5/materials" },
            create = { materials.createMaterial("p5", "Matériau ${counter++}", "kg").localId },
            correct = null,
            row = { db.materialDao().findByLocalId(it) }, remove = { db.materialDao().deleteByLocalId(it) },
            frozen = listOf(duplicateMaterial, invalidAmount, unknownCode), sentAgain = listOf(inactive, notAllowed),
        ),
        Table(
            "entry", SyncIssueTarget.ENTRY, { "/stages/90/logs/$entryDay/works" },
            create = {
                entryDay = "2026-10-" + (10 + counter++).toString()
                logs.createWorkEntry("st90", entryDay).entryLocalId
            },
            correct = { logs.updateEntry(it, "Résumé corrigé ${counter++}") },
            row = { db.dailyEntryDao().findByLocalId(it) }, remove = { db.dailyEntryDao().deleteByLocalId(it) },
            frozen = listOf(duplicateEntry, pastDay, wrongEntryType, unknownCode), sentAgain = listOf(inactive, notAllowed),
        ),
        Table(
            "purchase line", SyncIssueTarget.PURCHASE_LINE, { "/entries/900/purchase-lines" },
            create = { purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 5.0, 6.0, null)) },
            correct = { purchaseLines.updateLine(it, UpdatePurchaseLineInput(2.0, 6.0, null)) },
            row = { db.purchaseLineDao().findByLocalId(it) }, remove = { db.purchaseLineDao().deleteByLocalId(it) },
            frozen = listOf(invalidAmount, wrongMaterial, wrongEntryType, pastDay, unknownCode), sentAgain = listOf(inactive, notAllowed),
        ),
        Table(
            "consumption line", SyncIssueTarget.CONSUMPTION_LINE, { "/entries/901/consumption-lines" },
            create = { consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 5.0)) },
            correct = { consumptionLines.updateLine(it, UpdateConsumptionLineInput(2.0)) },
            row = { db.consumptionLineDao().findByLocalId(it) }, remove = { db.consumptionLineDao().deleteByLocalId(it) },
            frozen = listOf(invalidAmount, wrongMaterial, wrongEntryType, pastDay, unknownCode), sentAgain = listOf(notEnoughStock, inactive, notAllowed),
        ),
    )

    private fun posts(path: String) = backend.receivedMethods.count { it == "POST $path" }

    private fun writes() = backend.receivedMethods.filter { !it.startsWith("GET ") }

    private fun theServerRefuses(path: String, refusal: Refusal) {
        backend.refuseWith = { request ->
            if (request.method.value == "POST" && request.url.encodedPath.endsWith(path)) {
                ServerRefusal(refusal.status, refusal.code, refusal.withFieldErrors, refusal.detail)
            } else {
                null
            }
        }
    }

    private suspend fun threePasses() = repeat(3) { engine.syncNow() }

    private suspend fun listed(target: SyncIssueTarget, id: String) = issues.observeIssues().first().singleOrNull { it.target == target && it.localId == id }

    @Test
    fun a_creation_refused_for_a_reason_only_a_correction_can_lift_is_sent_once_however_many_passes_follow() = runTest {
        aSyncedSite()
        for (table in tables) {
            for (refusal in table.frozen) {
                val id = table.create()
                val path = table.createPath()
                val before = posts(path)
                theServerRefuses(path, refusal)

                threePasses()

                val context = "${table.name}, $refusal"
                assertEquals(1, posts(path) - before, "$context: three passes, one request")
                val row = table.row(id)!!
                assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, PendingOp.CREATE, refusal.keptCode), listOf(row.syncStatus, row.pendingOp, row.serverErrorCode), context)
                assertEquals(listOf<Any?>(SyncIssueKind.REFUSED, refusal.shownReason), listed(table.target, id)?.issue?.let { listOf<Any?>(it.kind, it.reason) }, "$context: still listed to review")
                backend.refuseWith = null
                table.remove(id)
            }
        }
    }

    @Test
    fun a_creation_refused_for_a_reason_that_depends_on_something_else_is_still_sent_at_every_pass_and_leaves_by_itself() = runTest {
        aSyncedSite()
        for (table in tables) {
            for (refusal in table.sentAgain) {
                val id = table.create()
                val path = table.createPath()
                val before = posts(path)
                theServerRefuses(path, refusal)

                threePasses()

                val context = "${table.name}, $refusal"
                assertEquals(3, posts(path) - before, "$context: three passes, three requests, as before")
                assertEquals(refusal.reason, listed(table.target, id)?.issue?.reason, context)

                backend.refuseWith = null
                assertEquals(SyncOutcome.Synced, engine.syncNow(), context)
                assertEquals(4, posts(path) - before, context)
                assertEquals(SyncStatus.SYNCED, table.row(id)!!.syncStatus, "$context: once the cause is lifted the entry leaves without any action")
            }
        }
    }

    @Test
    fun a_frozen_creation_leaves_once_when_the_user_corrects_it() = runTest {
        aSyncedSite()
        for (table in tables) {
            val correct = table.correct ?: continue
            val id = table.create()
            val path = table.createPath()
            val before = posts(path)
            theServerRefuses(path, table.frozen.first())
            threePasses()
            assertEquals(1, posts(path) - before, table.name)

            correct(id)
            val corrected = table.row(id)!!
            assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), listOf(corrected.syncStatus, corrected.pendingOp, corrected.lastSyncError, corrected.serverErrorCode), table.name)
            backend.refuseWith = null
            threePasses()

            assertEquals(2, posts(path) - before, "${table.name}: the corrected entry is sent once")
            assertEquals(SyncStatus.SYNCED, table.row(id)!!.syncStatus, table.name)
            assertEquals(null, listed(table.target, id), table.name)
        }
    }

    @Test
    fun a_correction_the_server_refuses_again_is_sent_once_then_waits_again() = runTest {
        aSyncedSite()
        val line = consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 5.0))
        theServerRefuses("/entries/901/consumption-lines", invalidAmount)
        threePasses()

        consumptionLines.updateLine(line, UpdateConsumptionLineInput(4.0))
        threePasses()
        consumptionLines.updateLine(line, UpdateConsumptionLineInput(3.0))
        threePasses()

        assertEquals(3, posts("/entries/901/consumption-lines"), "one request for the first value and one for each correction")
        assertEquals(listOf<Any?>(3.0, SyncStatus.CONFLICTED, "INVALID_AMOUNT"), db.consumptionLineDao().findByLocalId(line)!!.let { listOf(it.quantity, it.syncStatus, it.serverErrorCode) })
    }

    @Test
    fun what_was_created_under_a_frozen_parent_stays_waiting_and_is_never_sent() = runTest {
        aSyncedSite()
        val project = projects.createProject(CreateProjectInput("Projet figé", null, null, "EUR", "Europe/Paris"))
        val stageUnderProject = stages.createStage(CreateStageInput(project, "Sous le projet figé", null, null, null, null))
        val materialUnderProject = materials.createMaterial(project, "Sable", "t").localId
        val stage = stages.createStage(CreateStageInput("p5", "Étape figée", null, null, null, null))
        val entryUnderStage = logs.createPurchaseEntry(stage, "2026-10-02").entryLocalId
        val lineUnderStage = purchaseLines.createLine(entryUnderStage, CreatePurchaseLineInput("m7", 1.0, 6.0, null))
        val entry = logs.createPurchaseEntry("st90", "2026-10-03").entryLocalId
        val lineUnderEntry = purchaseLines.createLine(entry, CreatePurchaseLineInput("m7", 2.0, 6.0, null))
        val photoUnderEntry = attachments.addAttachment(entry, ByteArray(8), "bon.jpg", "image/jpeg").localId
        val material = materials.createMaterial("p5", "Gravier", "t").localId
        val lineWithMaterial = consumptionLines.createLine("e901", CreateConsumptionLineInput(material, 1.0))
        backend.refuseWith = { request ->
            val path = request.url.encodedPath
            when {
                request.method.value != "POST" -> null
                path.endsWith("/projects") || path.endsWith("/projects/5/stages") || path.endsWith("/projects/5/materials") ->
                    ServerRefusal(HttpStatusCode.BadRequest, "VALIDATION_FAILED", withFieldErrors = true)
                path.endsWith("/purchases") -> ServerRefusal(HttpStatusCode.Forbidden, "ENTRY_DATE_RESTRICTED")
                else -> null
            }
        }

        threePasses()

        assertEquals(
            listOf("POST /projects", "POST /projects/5/stages", "POST /projects/5/materials", "POST /stages/90/logs/2026-10-03/purchases"),
            writes(), "one request for each frozen parent, none for what waits under them",
        )
        val children = listOf(
            db.stageDao().findByLocalId(stageUnderProject), db.materialDao().findByLocalId(materialUnderProject), db.dailyEntryDao().findByLocalId(entryUnderStage),
            db.purchaseLineDao().findByLocalId(lineUnderStage), db.purchaseLineDao().findByLocalId(lineUnderEntry), db.attachmentDao().findByLocalId(photoUnderEntry),
            db.consumptionLineDao().findByLocalId(lineWithMaterial),
        )
        children.forEach { child ->
            assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), listOf(child!!.syncStatus, child.pendingOp, child.lastSyncError, child.serverErrorCode), "$child")
        }
        val shown = issues.observeIssues().first().associate { it.localId to it.issue.kind }
        assertEquals(
            setOf(SyncIssueKind.BLOCKED_BY_PARENT),
            listOf(stageUnderProject, materialUnderProject, entryUnderStage, lineUnderStage, lineUnderEntry, photoUnderEntry, lineWithMaterial).map { shown[it] }.toSet(),
            "each one is shown as waiting on its refused parent",
        )
        assertEquals(setOf(SyncIssueKind.REFUSED), listOf(project, stage, entry, material).map { shown[it] }.toSet())
    }

    @Test
    fun the_list_to_review_the_badge_the_markers_and_the_sign_out_count_are_the_same_after_one_pass_and_after_three() = runTest {
        aSyncedSite()
        val frozenEntry = logs.createPurchaseEntry("st90", "2026-10-04").entryLocalId
        val waitingLine = purchaseLines.createLine(frozenEntry, CreatePurchaseLineInput("m7", 2.0, 6.0, null))
        val resentLine = consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 500.0))
        backend.refuseWith = { request ->
            val path = request.url.encodedPath
            when {
                request.method.value != "POST" -> null
                path.endsWith("/purchases") -> ServerRefusal(HttpStatusCode.Forbidden, "ENTRY_DATE_RESTRICTED")
                path.endsWith("/entries/901/consumption-lines") -> ServerRefusal(HttpStatusCode.Conflict, "INSUFFICIENT_STOCK")
                else -> null
            }
        }
        suspend fun whatTheUserSees(): List<Any?> {
            val list = issues.observeIssues().first()
            val markers = SyncIssueMarkers(list)
            return listOf(
                list.map { Triple(it.key, it.issue.kind, it.issue.reason) }.sortedBy { it.first },
                issues.observeIssueCount().first(),
                listOf(
                    markers.of(SyncIssueTarget.ENTRY, frozenEntry)?.issue?.kind, markers.of(SyncIssueTarget.PURCHASE_LINE, waitingLine)?.issue?.kind,
                    markers.of(SyncIssueTarget.CONSUMPTION_LINE, resentLine)?.issue?.kind, markers.of(SyncIssueTarget.ENTRY, "e900")?.issue?.kind,
                ),
                unsent.unsentByKind(),
                unsent.countUnsynced(),
            )
        }

        engine.syncNow()
        val afterOnePass = whatTheUserSees()
        engine.syncNow()
        engine.syncNow()

        assertEquals(afterOnePass, whatTheUserSees(), "nothing the user sees depends on whether the entry is sent again")
        assertEquals(
            listOf<Any?>(
                listOf(
                    Triple("CONSUMPTION_LINE:$resentLine", SyncIssueKind.REFUSED, RefusalReason.INSUFFICIENT_STOCK),
                    Triple("ENTRY:$frozenEntry", SyncIssueKind.REFUSED, RefusalReason.ENTRY_DATE_RESTRICTED),
                    Triple("PURCHASE_LINE:$waitingLine", SyncIssueKind.BLOCKED_BY_PARENT, null),
                ).sortedBy { it.first },
                2,
                listOf(SyncIssueKind.REFUSED, SyncIssueKind.BLOCKED_BY_PARENT, SyncIssueKind.REFUSED, null),
                UnsentWrites(entries = 1, lines = 2),
                3,
            ),
            afterOnePass,
        )
        assertEquals(listOf(1, 3), listOf(posts("/stages/90/logs/2026-10-04/purchases"), posts("/entries/901/consumption-lines")), "the frozen entry once, the other at every pass")
    }

    @Test
    fun a_creation_refused_before_codes_were_kept_is_frozen_except_a_plan_limit_which_is_still_sent() = runTest {
        aSyncedSite()
        db.stageDao().upsert(localStage("st-old", projectLocalId = "p5", name = "Refusée avant", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
        db.projectDao().upsert(localProject("p-old", name = "Au-delà de la limite", syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.PLAN_LIMIT))

        threePasses()

        assertEquals(listOf(0, 1), listOf(posts("/projects/5/stages"), posts("/projects")), "no code: unknown reason, frozen; a plan limit is recognised without its code and sent")
        assertEquals(SyncStatus.CONFLICTED, db.stageDao().findByLocalId("st-old")!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, db.projectDao().findByLocalId("p-old")!!.syncStatus, "the limit is not there on this server: the project left by itself")
    }

    @Test
    fun unchanged_in_this_tranche_a_photo_refused_for_another_reason_than_its_file_is_still_uploaded_at_every_pass() = runTest {
        aSyncedSite()
        val photo = attachments.addAttachment("e900", ByteArray(8), "bon.jpg", "image/jpeg")
        theServerRefuses("/entries/900/attachments", inactive)

        threePasses()

        assertEquals(3, posts("/entries/900/attachments"), "attachments are handled with the uploads (group D)")
        assertEquals(SyncStatus.CONFLICTED, db.attachmentDao().findByLocalId(photo.localId)!!.syncStatus)
    }

    @Test
    fun a_pending_change_and_a_pending_deletion_of_a_synced_element_are_sent_as_before() = runTest {
        aSyncedSite()
        stages.updateStage("st90", UpdateStageInput("Gros œuvre et fondations", null, null, null, null, StageStatus.IN_PROGRESS))
        purchaseLines.deleteLine("pl5000")

        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertTrue(writes().containsAll(listOf("PATCH /stages/90", "DELETE /purchase-lines/5000")), "${writes()}")
        assertEquals("Gros œuvre et fondations", backend.stages.single().name)
    }

    @Test
    fun current_behaviour_not_a_rule_a_duplicate_material_with_no_namesake_loses_its_code_and_reads_as_an_unknown_reason() = runTest {
        aSyncedSite()
        val material = materials.createMaterial("p5", "Gravier", "t").localId
        theServerRefuses("/projects/5/materials", duplicateMaterial)

        threePasses()

        val row = db.materialDao().findByLocalId(material)!!
        assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, null), listOf(row.syncStatus, row.serverErrorCode), "the read of the namesakes that follows the refusal clears the code just received")
        assertEquals(RefusalReason.UNKNOWN, listed(SyncIssueTarget.MATERIAL, material)?.issue?.reason)
        assertEquals(1, posts("/projects/5/materials"), "frozen either way: a duplicate and an unknown reason both wait for the user")
    }
}
