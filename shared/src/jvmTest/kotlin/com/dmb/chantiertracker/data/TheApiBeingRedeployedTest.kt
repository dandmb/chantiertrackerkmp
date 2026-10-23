package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.RedeployAnswer
import com.dmb.chantiertracker.support.ServerAttachment
import com.dmb.chantiertracker.support.ServerConsumptionLine
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerInvitation
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerPurchaseLine
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.everyRow
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localInvitation
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TheApiBeingRedeployedTest {

    private class Device {
        val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        val backend = FakeProjectBackend()
        val fileStore = FakeAttachmentFileStore()
        val clock = MutableClock(1_800_000_000_000L)
        private var pulledIds = 0

        val engine = SyncEngine(
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
            connectivity = FakeConnectivityObserver(initiallyOnline = true),
            syncState = SyncStateHolder(),
            scope = AppCoroutineScope(),
            clock = clock,
            newLocalId = { "pulled-${pulledIds++}" },
            backgroundSync = FakeBackgroundSync(),
            awaitedServerVersions = db.syncIssueActionDao(),
        )

        suspend fun everyRow(): String = db.everyRow()

        suspend fun aSiteWithEveryKindOfPendingWrite() {
            backend.nextLogId = 2_000L
            backend.nextEntryId = 3_000L
            backend.seed(ServerProject(id = 5, name = "Villa"))
            backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
            backend.seedStage(ServerStage(id = 91, projectId = 5, name = "Clôture"))
            backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
            backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
            backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
            backend.seedLog(ServerLog(id = 801, stageId = 90, date = "2026-09-04"))
            backend.seedEntry(ServerEntry(id = 902, dailyLogId = 801, type = "WORK", summary = "Doublon"))
            backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
            backend.seedPurchaseLine(ServerPurchaseLine(id = 70, entryId = 900, materialId = 7, quantity = 10.0, unitPrice = 6.0))
            backend.seedPurchaseLine(ServerPurchaseLine(id = 71, entryId = 900, materialId = 7, quantity = 4.0, unitPrice = 6.0))
            backend.seedConsumptionLine(ServerConsumptionLine(id = 80, entryId = 901, materialId = 7, quantity = 2.0))
            backend.seedConsumptionLine(ServerConsumptionLine(id = 81, entryId = 901, materialId = 7, quantity = 1.0))
            backend.seedAttachment(ServerAttachment(id = 60, entryId = 901, originalName = "dalle.jpg"))
            backend.seedAttachment(ServerAttachment(id = 61, entryId = 901, originalName = "flou.jpg"))
            backend.seedInvitation(ServerInvitation(id = 40, projectId = 5, email = "sam@chantier.dev"))

            val synced = SyncStatus.SYNCED
            val none = PendingOp.NONE
            val pending = SyncStatus.PENDING
            db.projectDao().upsert(localProject("p5", name = "Villa rénovée", serverId = 5, pendingOp = PendingOp.UPDATE, syncStatus = pending))
            db.projectDao().upsert(localProject("p-new", name = "Maison neuve", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
            db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
            db.stageDao().upsert(localStage("st91", projectLocalId = "p5", name = "Clôture", serverId = 91, pendingOp = PendingOp.DELETE, syncStatus = pending))
            db.stageDao().upsert(localStage("st-new", projectLocalId = "p5", name = "Toiture", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
            db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
            db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
            db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = none, syncStatus = synced))
            db.dailyLogDao().upsert(localDailyLog("l801", stageLocalId = "st90", serverId = 801, date = "2026-09-04"))
            db.dailyEntryDao().upsert(localDailyEntry("e902", dailyLogLocalId = "l801", type = "WORK", summary = "Doublon", serverId = 902, pendingOp = PendingOp.DELETE, syncStatus = pending))
            db.dailyLogDao().upsert(localDailyLog("l-new", stageLocalId = "st90", serverId = null, date = "2026-09-06"))
            db.dailyEntryDao().upsert(localDailyEntry("e-new", dailyLogLocalId = "l-new", type = "WORK", summary = "Ferraillage", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
            db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = none, syncStatus = synced))
            db.materialDao().upsert(localMaterial("m-new", projectLocalId = "p5", name = "Sable", unit = "t", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
            db.purchaseLineDao().upsert(localPurchaseLine("pl70", entryLocalId = "e900", materialLocalId = "m7", quantity = 10.0, unitPrice = 6.0, serverId = 70, pendingOp = none, syncStatus = synced))
            db.purchaseLineDao().upsert(localPurchaseLine("pl71", entryLocalId = "e900", materialLocalId = "m7", quantity = 4.0, unitPrice = 6.0, serverId = 71, pendingOp = PendingOp.DELETE, syncStatus = pending))
            db.purchaseLineDao().upsert(localPurchaseLine("pl-new", entryLocalId = "e900", materialLocalId = "m7", quantity = 3.0, unitPrice = 6.0))
            db.consumptionLineDao().upsert(localConsumptionLine("cl80", entryLocalId = "e901", materialLocalId = "m7", quantity = 2.0, serverId = 80, pendingOp = none, syncStatus = synced))
            db.consumptionLineDao().upsert(localConsumptionLine("cl81", entryLocalId = "e901", materialLocalId = "m7", quantity = 1.0, serverId = 81, pendingOp = PendingOp.DELETE, syncStatus = pending))
            db.consumptionLineDao().upsert(localConsumptionLine("cl-new", entryLocalId = "e901", materialLocalId = "m7", quantity = 1.5))
            val photo = fileStore.save(byteArrayOf(4, 5, 6), "fissure.jpg")
            db.attachmentDao().upsert(localAttachment("a60", entryLocalId = "e901", originalName = "dalle.jpg", serverId = 60, pendingOp = none, syncStatus = synced))
            db.attachmentDao().upsert(localAttachment("a61", entryLocalId = "e901", originalName = "flou.jpg", serverId = 61, pendingOp = PendingOp.DELETE, syncStatus = pending))
            db.attachmentDao().upsert(localAttachment("a-new", entryLocalId = "e901", localPath = photo, originalName = "fissure.jpg", sizeBytes = 3))
            db.invitationDao().upsertAll(listOf(localInvitation(40, projectLocalId = "p5", email = "sam@chantier.dev")))
        }

        fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

        fun close() = db.close()
    }

    private val devices = mutableListOf<Device>()

    private fun device() = Device().also { devices += it }

    @AfterTest fun closeAll() = devices.forEach { it.close() }

    private class Step(val name: String, val run: suspend (Device) -> SyncOutcome)

    // An unavailable service fails a pass exactly where a cut does; only its message differs.
    private fun SyncOutcome.asSeenByTheEngine(): SyncOutcome =
        if (this == SyncOutcome.Failed(DomainException.ServiceUnavailable)) SyncOutcome.Failed(DomainException.Network) else this

    private val aDayOfPasses = listOf(
        Step("pass") { it.engine.syncNow() },
        Step("project opened") { it.engine.syncProject("p5") },
        Step("stage opened") { it.engine.syncStage("st90") },
        Step("day opened") { it.engine.syncLog("l800") },
        Step("pass 2 min later") { it.after(2.minutes); it.engine.syncNow() },
        Step("pass 1 h later") { it.after(1.hours); it.engine.syncNow() },
        Step("pass 6 h later") { it.after(6.hours); it.engine.syncNow() },
    )

    // The reference is the network cut: a 404 that is not the backend's own answer must
    // leave every table, every wait and every request exactly as a cut does.
    private fun aProxy404IsACut(on: String, answer: RedeployAnswer = RedeployAnswer.PROXY_TEXT, affected: (HttpRequestData) -> Boolean) = runTest {
        val cut = device()
        val redeploy = device()
        cut.aSiteWithEveryKindOfPendingWrite()
        redeploy.aSiteWithEveryKindOfPendingWrite()
        cut.backend.cutTheNetworkFor = affected
        redeploy.backend.redeployingFor = affected
        redeploy.backend.redeployAnswer = answer

        for (step in aDayOfPasses) {
            val outcomeOfTheCut = step.run(cut)
            val outcomeOfTheRedeploy = step.run(redeploy)
            assertEquals(cut.everyRow(), redeploy.everyRow(), "$on, $answer, after « ${step.name} »: the local database must be the one a network cut leaves")
            assertEquals(outcomeOfTheCut, outcomeOfTheRedeploy.asSeenByTheEngine(), "$on, $answer, outcome of « ${step.name} »")
            assertEquals(cut.backend.receivedMethods, redeploy.backend.receivedMethods, "$on, $answer, requests sent up to « ${step.name} »: same sends, same spacing as a cut")
        }
    }

    @Test
    fun every_request_answered_by_the_proxy_is_treated_as_a_network_cut() =
        aProxy404IsACut("every request") { true }

    @Test
    fun every_form_of_proxy_404_is_treated_as_a_network_cut() {
        RedeployAnswer.entries.forEach { answer -> aProxy404IsACut("every request", answer) { true } }
    }

    @Test
    fun reading_a_project_answered_by_the_proxy_is_a_cut_not_a_project_deleted_on_the_server() =
        aProxy404IsACut("GET /projects/5") { it.method == HttpMethod.Get && it.url.encodedPath.endsWith("/projects/5") }

    @Test
    fun reading_a_stage_answered_by_the_proxy_is_a_cut_not_a_stage_deleted_on_the_server() =
        aProxy404IsACut("GET /stages/90") { it.method == HttpMethod.Get && it.url.encodedPath.endsWith("/stages/90") }

    @Test
    fun reading_the_invitations_answered_by_the_proxy_keeps_them() =
        aProxy404IsACut("GET invitations") { it.method == HttpMethod.Get && it.url.encodedPath.endsWith("/invitations") }

    @Test
    fun sending_the_creations_answered_by_the_proxy_never_turns_them_into_refusals() =
        aProxy404IsACut("every POST") { it.method == HttpMethod.Post }

    @Test
    fun sending_a_photo_answered_by_the_proxy_never_turns_it_into_a_refusal() =
        aProxy404IsACut("POST attachments") { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/attachments") }

    @Test
    fun sending_the_updates_answered_by_the_proxy_never_turns_them_into_refusals() =
        aProxy404IsACut("every PATCH") { it.method == HttpMethod.Patch }

    @Test
    fun pending_deletes_answered_by_the_proxy_are_not_taken_as_done() =
        aProxy404IsACut("every DELETE") { it.method == HttpMethod.Delete }

    @Test
    fun a_whole_day_of_passes_during_a_redeploy_leaves_the_local_database_exactly_as_it_was() = runTest {
        val phone = device()
        phone.aSiteWithEveryKindOfPendingWrite()
        val before = phone.everyRow()
        phone.backend.redeployingFor = { true }

        for (step in aDayOfPasses) {
            val outcome = step.run(phone)
            assertEquals(SyncOutcome.Failed(DomainException.ServiceUnavailable), outcome, "« ${step.name} » fails as an unavailable service")
            assertEquals(before, phone.everyRow(), "after « ${step.name} »: nothing erased, nothing refused, no delete taken as done")
        }
    }

    @Test
    fun once_the_api_is_back_every_pending_write_leaves_and_nothing_is_refused() = runTest {
        val phone = device()
        phone.aSiteWithEveryKindOfPendingWrite()
        phone.backend.redeployingFor = { true }
        repeat(3) { phone.engine.syncNow() }
        phone.backend.redeployingFor = null
        phone.backend.receivedMethods.clear()

        phone.after(1.hours)
        assertEquals(SyncOutcome.Synced, phone.engine.syncNow())

        val db = phone.db
        listOf(
            db.projectDao().findByLocalId("p5"), db.projectDao().findByLocalId("p-new"), db.stageDao().findByLocalId("st-new"),
            db.materialDao().findByLocalId("m-new"), db.dailyEntryDao().findByLocalId("e-new"), db.purchaseLineDao().findByLocalId("pl-new"),
            db.consumptionLineDao().findByLocalId("cl-new"), db.attachmentDao().findByLocalId("a-new"),
        ).forEach { row -> assertEquals(SyncStatus.SYNCED, row?.syncStatus, "$row") }
        listOf(
            db.stageDao().findByLocalId("st91"), db.dailyEntryDao().findByLocalId("e902"), db.purchaseLineDao().findByLocalId("pl71"),
            db.consumptionLineDao().findByLocalId("cl81"), db.attachmentDao().findByLocalId("a61"),
        ).forEach { row -> assertEquals(null, row, "deleted on the server, then gone from the device") }
        listOf("/stages/91", "/entries/902", "/purchase-lines/71", "/consumption-lines/81", "/attachments/61").forEach { path ->
            assertEquals(1, phone.backend.receivedMethods.count { it == "DELETE $path" }, "DELETE $path sent once the API is back, sent: ${phone.backend.receivedMethods}")
        }
        assertEquals(1, phone.backend.receivedMethods.count { it == "POST /entries/901/attachments" }, "the photo is sent once")
        assertEquals(listOf(40L), db.invitationDao().findForProject("p5").map { it.id })
    }
}
