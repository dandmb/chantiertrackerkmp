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
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UploadsOnAnUnreliableNetworkTest {

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

    private val uploads = "/entries/900/attachments"
    private var counter = 0

    private suspend fun aSyncedSite(projectStatus: String = "IN_PROGRESS") {
        backend.nextLogId = 2_000L
        backend.nextEntryId = 3_000L
        backend.seed(ServerProject(id = 5, name = "Villa", status = projectStatus))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        val synced = SyncStatus.SYNCED
        val none = PendingOp.NONE
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced).copy(status = projectStatus))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = none, syncStatus = synced))
    }

    private suspend fun aPhoto(name: String): String = attachments.addAttachment("e900", ByteArray(8), name, "image/jpeg").localId

    private suspend fun photo(id: String) = db.attachmentDao().findByLocalId(id)

    private fun posts(path: String) = backend.receivedMethods.count { it == "POST $path" }

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    private suspend fun threePasses() = repeat(3) { engine.syncNow() }

    private fun isAnUpload(request: HttpRequestData) = request.method.value == "POST" && request.url.encodedPath.endsWith(uploads)

    private fun theServerAnswersUploadsWith(status: HttpStatusCode, code: String? = null, detail: String = "Refusé.", withFieldErrors: Boolean = false) {
        backend.refuseWith = { request -> if (isAnUpload(request)) ServerRefusal(status, code, withFieldErrors, detail) else null }
    }

    private suspend fun assertStillWaitingWithoutAnyRefusal(id: String, context: String) {
        val row = photo(id)!!
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), listOf(row.syncStatus, row.pendingOp, row.lastSyncError, row.serverErrorCode), "$context: still waiting, no refusal written")
        assertEquals(emptyList(), issues.observeIssues().first().map { it.key }, "$context: nothing to review")
        assertEquals(0, issues.observeIssueCount().first(), context)
        assertEquals(1, unsent.countUnsynced(), "$context: it still counts as not sent")
    }

    @Test
    fun a_rate_limited_upload_waits_for_the_time_the_server_gave_and_the_rest_of_the_pass_goes_on() = runTest {
        aSyncedSite()
        backend.seedAttachment(ServerAttachment(id = 50, entryId = 900, originalName = "ancienne.jpg"))
        engine.syncLog("l800")
        val old = db.attachmentDao().findByServerId(50)!!.localId
        attachments.deleteAttachment(old)
        val stage = stages.createStage(CreateStageInput("p5", "Toiture", null, null, null, null))
        val first = aPhoto("bon-1.jpg")
        val second = aPhoto("bon-2.jpg")
        val third = aPhoto("bon-3.jpg")
        backend.uploadsLeftBeforeRateLimit = 1
        backend.rateLimitRetryAfterSeconds = 30
        backend.seed(ServerProject(id = 6, name = "Arrivé pendant la passe"))
        backend.receivedMethods.clear()

        assertEquals(SyncOutcome.Synced, engine.syncNow(), "a rate limit on uploads does not fail the pass")

        assertEquals(2, posts(uploads), "one accepted, one rate limited, and the third is not even tried: the quota is per account")
        assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.PENDING, SyncStatus.PENDING), listOf(first, second, third).map { photo(it)!!.syncStatus })
        listOf(second, third).forEach { id -> assertEquals(listOf<Any?>(null, null), photo(id)!!.let { listOf(it.lastSyncError, it.serverErrorCode) }, "a rate limit is not a refusal") }
        assertEquals(emptyList(), issues.observeIssues().first().map { it.key })
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stage)!!.syncStatus, "the other routes are sent")
        assertTrue("DELETE /attachments/50" in backend.receivedMethods, "deleting a file is another route: it is sent in the same pass")
        assertTrue(db.projectDao().findByServerId(6) != null, "and the pull is done")

        backend.uploadsLeftBeforeRateLimit = 5
        after(30.seconds - 1.milliseconds)
        threePasses()
        assertEquals(2, posts(uploads), "nothing is uploaded before the time the server gave")

        after(1.milliseconds)
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(4, posts(uploads), "both waiting files leave once the time has come")
        assertEquals(setOf(SyncStatus.SYNCED), listOf(first, second, third).map { photo(it)!!.syncStatus }.toSet())
        assertEquals(listOf("bon-1.jpg", "bon-2.jpg", "bon-3.jpg"), backend.attachments.filter { it.entryId == 900L }.map { it.originalName }.sorted())
    }

    @Test
    fun a_rate_limit_without_retry_after_waits_one_minute() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        backend.uploadsLeftBeforeRateLimit = 0
        backend.rateLimitRetryAfterSeconds = null

        assertEquals(SyncOutcome.Synced, engine.syncNow())
        backend.uploadsLeftBeforeRateLimit = 5
        after(1.minutes - 1.milliseconds)
        threePasses()
        assertEquals(1, posts(uploads), "the server's quotas all refill within one minute: not before")

        after(1.milliseconds)
        engine.syncNow()
        assertEquals(2, posts(uploads))
        assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus)
    }

    @Test
    fun a_rate_limit_is_not_counted_as_a_step_of_the_spacing() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        backend.uploadsLeftBeforeRateLimit = 0
        repeat(4) {
            engine.syncNow()
            after(3.seconds)
        }
        assertEquals(4, posts(uploads), "each time the quota is still empty the file waits what the server says, no more")

        backend.uploadsLeftBeforeRateLimit = null
        theServerAnswersUploadsWith(HttpStatusCode.InternalServerError)
        engine.syncNow()
        after(15.minutes)
        backend.refuseWith = null
        engine.syncNow()
        assertEquals(6, posts(uploads), "a server error after rate limits starts the spacing at fifteen minutes")
        assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus)
    }

    @Test
    fun a_server_error_on_an_upload_is_not_a_refusal_the_file_keeps_waiting_and_is_spaced() = runTest {
        aSyncedSite()
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)) {
            val photo = aPhoto("bon-${status.value}.jpg")
            val before = posts(uploads)
            theServerAnswersUploadsWith(status)
            backend.seed(ServerProject(id = 1_000L + status.value, name = "Arrivé pendant la panne ${status.value}"))

            assertEquals(SyncOutcome.Synced, engine.syncNow(), "$status: the pass goes on")
            assertTrue(db.projectDao().findByServerId(1_000L + status.value) != null, "$status: the pull is done")
            threePasses()

            assertEquals(1, posts(uploads) - before, "$status: not uploaded again at every pass")
            assertStillWaitingWithoutAnyRefusal(photo, "$status")
            after(15.minutes - 1.milliseconds)
            threePasses()
            assertEquals(1, posts(uploads) - before, "$status")
            after(1.milliseconds)
            threePasses()
            assertEquals(2, posts(uploads) - before, "$status: tried again fifteen minutes later")
            assertStillWaitingWithoutAnyRefusal(photo, "$status")

            backend.refuseWith = null
            after(1.hours - 1.milliseconds)
            threePasses()
            assertEquals(2, posts(uploads) - before, "$status: then one hour later")
            after(1.milliseconds)
            engine.syncNow()
            assertEquals(3, posts(uploads) - before, "$status")
            assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus, "$status")
        }
    }

    @Test
    fun a_network_failure_on_an_upload_is_not_a_refusal_the_file_keeps_waiting_and_is_spaced() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        backend.cutTheNetworkFor = ::isAnUpload
        backend.seed(ServerProject(id = 6, name = "Arrivé pendant la coupure"))

        assertEquals(SyncOutcome.Synced, engine.syncNow(), "only the upload failed: the pass goes on")
        assertTrue(db.projectDao().findByServerId(6) != null, "the pull is done")
        threePasses()

        assertEquals(1, posts(uploads))
        assertStillWaitingWithoutAnyRefusal(photo, "network cut")
        after(15.minutes)
        threePasses()
        assertEquals(2, posts(uploads), "tried again fifteen minutes later")
        assertStillWaitingWithoutAnyRefusal(photo, "network cut again")

        backend.cutTheNetworkFor = null
        after(1.hours)
        engine.syncNow()
        assertEquals(3, posts(uploads))
        assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus)
        assertEquals(1, backend.attachments.count { it.entryId == 900L }, "the server never got the first two: one file, no duplicate")
    }

    @Test
    fun a_file_that_failed_does_not_hold_back_the_other_files() = runTest {
        aSyncedSite()
        val failing = aPhoto("bon-1.jpg")
        var uploadsSeen = 0
        backend.cutTheNetworkFor = { request -> isAnUpload(request) && uploadsSeen++ == 0 }
        val other = aPhoto("bon-2.jpg")

        engine.syncNow()

        assertEquals(listOf(SyncStatus.PENDING, SyncStatus.SYNCED), listOf(photo(failing)!!.syncStatus, photo(other)!!.syncStatus), "a failure is about one file, a rate limit about the account")
    }

    @Test
    fun a_server_error_on_any_of_the_six_other_tables_is_never_written_as_a_refusal() = runTest {
        aSyncedSite()
        val creations: List<Triple<String, suspend () -> String, suspend (String) -> SyncedRow?>> = listOf(
            Triple("/projects", { projects.createProject(CreateProjectInput("Chantier", null, null, "EUR", "Europe/Paris")) }, { db.projectDao().findByLocalId(it) }),
            Triple("/projects/5/stages", { stages.createStage(CreateStageInput("p5", "Étape", null, null, null, null)) }, { db.stageDao().findByLocalId(it) }),
            Triple("/projects/5/materials", { materials.createMaterial("p5", "Sable ${counter++}", "t").localId }, { db.materialDao().findByLocalId(it) }),
            Triple("/works", { logs.createWorkEntry("st90", "2026-10-${10 + counter++}").entryLocalId }, { db.dailyEntryDao().findByLocalId(it) }),
            Triple("/entries/900/purchase-lines", { purchaseLines.createLine("e900", CreatePurchaseLineInput("m7", 5.0, 6.0, null)) }, { db.purchaseLineDao().findByLocalId(it) }),
            Triple("/entries/901/consumption-lines", { consumptionLines.createLine("e901", CreateConsumptionLineInput("m7", 5.0)) }, { db.consumptionLineDao().findByLocalId(it) }),
        )
        for ((path, create, row) in creations) {
            for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable)) {
                val id = create()
                backend.refuseWith = { request -> if (request.method.value == "POST" && request.url.encodedPath.endsWith(path)) ServerRefusal(status, null) else null }

                engine.syncNow()

                val after = row(id)!!
                assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null), listOf(after.syncStatus, after.pendingOp, after.lastSyncError, after.serverErrorCode), "$path, $status")
                assertEquals(emptyList(), issues.observeIssues().first().filter { it.issue.kind != SyncIssueKind.BLOCKED_BY_PARENT }.map { it.key }, "$path, $status: nothing to review")
                backend.refuseWith = null
                assertEquals(SyncOutcome.Synced, engine.syncNow(), "$path, $status: it leaves at the next pass once the server is back")
                assertEquals(SyncStatus.SYNCED, row(id)!!.syncStatus, "$path, $status")
            }
        }
    }

    @Test
    fun an_upload_refused_for_a_reason_only_the_user_can_lift_is_sent_once_and_never_again() = runTest {
        aSyncedSite()
        val frozen = listOf(
            Triple(HttpStatusCode.BadRequest, "VALIDATION_FAILED", RefusalReason.INVALID_VALUE),
            Triple(HttpStatusCode.Forbidden, "ENTRY_DATE_RESTRICTED", RefusalReason.ENTRY_DATE_RESTRICTED),
            Triple(HttpStatusCode.BadRequest, "A_CODE_FROM_A_NEWER_SERVER", RefusalReason.UNKNOWN),
            Triple(HttpStatusCode.MethodNotAllowed, null, RefusalReason.UNKNOWN),
        )
        for ((status, code, reason) in frozen) {
            val photo = aPhoto("bon-$code.jpg")
            val before = posts(uploads)
            theServerAnswersUploadsWith(status, code, withFieldErrors = code == "VALIDATION_FAILED")

            threePasses()
            after(48.hours)
            threePasses()

            assertEquals(1, posts(uploads) - before, "$code")
            val row = photo(photo)!!
            assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, SyncError.REJECTED, code), listOf(row.syncStatus, row.lastSyncError, row.serverErrorCode), "$code")
            val listed = issues.observeIssues().first().single { it.localId == photo }
            assertEquals(listOf<Any?>(SyncIssueKind.REFUSED, reason, listOf(SyncIssueAction.DISCARD)), listOf(listed.issue.kind, listed.issue.reason, listed.actions), "$code")
            issues.discard(listed)
        }
    }

    @Test
    fun a_refused_file_stays_frozen_as_before() = runTest {
        aSyncedSite()
        for ((status, code) in listOf(HttpStatusCode.PayloadTooLarge to "ATTACHMENT_TOO_LARGE", HttpStatusCode.BadRequest to "INVALID_ATTACHMENT_TYPE", HttpStatusCode.BadRequest to "UNSUPPORTED_IMAGE")) {
            val photo = aPhoto("bon-$code.jpg")
            val before = posts(uploads)
            theServerAnswersUploadsWith(status, code)

            threePasses()
            after(48.hours)
            threePasses()

            assertEquals(1, posts(uploads) - before, code)
            val row = photo(photo)!!
            assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, SyncError.FILE_REFUSED, code), listOf(row.syncStatus, row.lastSyncError, row.serverErrorCode), code)
            assertEquals(RefusalReason.FILE_REFUSED, issues.observeIssues().first().single { it.localId == photo }.issue.reason, code)
        }
    }

    @Test
    fun an_upload_refused_for_a_reason_that_can_lift_is_spaced_like_any_other_creation() = runTest {
        aSyncedSite()
        val liftable = listOf(
            Triple(HttpStatusCode.Forbidden, "PROJECT_OR_STAGE_INACTIVE", "Refusé."),
            Triple(HttpStatusCode.Forbidden, "PROJECT_INSUFFICIENT_ROLE", "Refusé."),
            Triple(HttpStatusCode.Forbidden, "PLAN_LIMIT_EXCEEDED", "Vous avez atteint la limite de photos de votre formule."),
        )
        for ((status, code, detail) in liftable) {
            val photo = aPhoto("bon-$code.jpg")
            val before = posts(uploads)
            theServerAnswersUploadsWith(status, code, detail)

            threePasses()
            assertEquals(1, posts(uploads) - before, "$code: not uploaded again at every pass")
            after(15.minutes - 1.milliseconds)
            threePasses()
            assertEquals(1, posts(uploads) - before, code)
            after(1.milliseconds)
            threePasses()
            assertEquals(2, posts(uploads) - before, "$code: uploaded again fifteen minutes later")
            val listed = issues.observeIssues().first().single { it.localId == photo }
            assertEquals(listOf<Any?>(SyncIssueKind.REFUSED, listOf(SyncIssueAction.RETRY, SyncIssueAction.DISCARD)), listOf(listed.issue.kind, listed.actions), code)

            backend.refuseWith = null
            after(1.hours)
            engine.syncNow()
            assertEquals(3, posts(uploads) - before, code)
            assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus, code)
            assertEquals(1, backend.attachments.count { it.originalName == "bon-$code.jpg" }, "$code: one file on the server")
        }
    }

    @Test
    fun a_project_seen_reopened_sends_the_upload_refused_under_it_in_the_same_pass() = runTest {
        aSyncedSite(projectStatus = "SUSPENDED")
        val photo = aPhoto("bon-1.jpg")
        theServerAnswersUploadsWith(HttpStatusCode.Forbidden, "PROJECT_OR_STAGE_INACTIVE")
        threePasses()
        assertEquals(1, posts(uploads))

        backend.projects.single().status = "IN_PROGRESS"
        backend.refuseWith = null
        engine.syncNow()

        assertEquals(2, posts(uploads))
        assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus)
    }

    @Test
    fun the_plan_of_the_project_owner_seen_changed_sends_the_upload_refused_for_the_plan_limit() = runTest {
        aSyncedSite()
        engine.syncProject("p5")
        val photo = aPhoto("bon-1.jpg")
        theServerAnswersUploadsWith(HttpStatusCode.Forbidden, "PLAN_LIMIT_EXCEEDED", "Vous avez atteint la limite de photos de votre formule.")
        threePasses()
        engine.syncProject("p5")
        assertEquals(1, posts(uploads), "the same plan read again sends nothing")
        assertEquals(0, backend.receivedMethods.count { it == "GET /users/me/plan-usage" }, "the limit is the owner's: the plan of this account is not read for a file")

        backend.projects.single().ownerPlan = "LIBERTE"
        backend.refuseWith = null
        engine.syncProject("p5")

        assertEquals(2, posts(uploads), "sent in the pass that read the owner's new plan")
        assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus)
    }

    @Test
    fun an_upload_the_server_stored_but_whose_answer_was_lost_is_not_uploaded_a_second_time() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        val other = aPhoto("bon-2.jpg")
        var uploadsSeen = 0
        backend.loseTheAnswerOf = { request -> isAnUpload(request) && uploadsSeen++ == 0 }

        engine.syncNow()

        assertEquals(listOf("bon-1.jpg", "bon-2.jpg"), backend.attachments.map { it.originalName }, "the server stored both")
        assertStillWaitingWithoutAnyRefusal(photo, "answer lost")
        after(15.minutes)
        engine.syncNow()

        assertEquals(2, posts(uploads), "the file is recognised on the server: no second upload")
        assertEquals(2, backend.attachments.size, "no duplicate on the server")
        val stored = backend.attachments.single { it.originalName == "bon-1.jpg" }
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, stored.id), photo(photo)!!.let { listOf(it.syncStatus, it.serverId) })
        assertEquals(listOf(photo, other).sorted(), db.attachmentDao().findForEntry("e900").map { it.localId }.sorted(), "and no duplicate on the device")
    }

    @Test
    fun the_pull_of_the_day_recognises_the_upload_whose_answer_was_lost_instead_of_downloading_it_as_a_new_file() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        backend.loseTheAnswerOf = ::isAnUpload
        engine.syncNow()
        backend.loseTheAnswerOf = null

        engine.syncLog("l800")

        val rows = db.attachmentDao().findForEntry("e900")
        assertEquals(listOf(photo), rows.map { it.localId }, "one row on the device, the one the user added")
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, backend.attachments.single().id), rows.single().let { listOf(it.syncStatus, it.serverId) })
        assertEquals(0, backend.receivedMethods.count { it == "GET /attachments/${backend.attachments.single().id}" }, "its bytes are already here: nothing is downloaded")
        after(48.hours)
        threePasses()
        assertEquals(1, posts(uploads))
        assertEquals(1, backend.attachments.size)
    }

    @Test
    fun a_file_of_the_same_name_already_known_on_the_device_is_not_taken_for_the_lost_upload() = runTest {
        aSyncedSite()
        backend.seedAttachment(ServerAttachment(id = 50, entryId = 900, originalName = "bon-1.jpg"))
        engine.syncLog("l800")
        val photo = aPhoto("bon-1.jpg")
        backend.cutTheNetworkFor = ::isAnUpload
        engine.syncNow()
        backend.cutTheNetworkFor = null

        after(15.minutes)
        engine.syncNow()

        assertEquals(2, posts(uploads), "the server never stored it: it is uploaded")
        assertEquals(2, backend.attachments.size, "two different files that share a name")
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, true), photo(photo)!!.let { listOf(it.syncStatus, it.serverId != 50L) })
    }

    @Test
    fun a_file_of_another_name_added_elsewhere_is_not_taken_for_the_lost_upload() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        backend.cutTheNetworkFor = ::isAnUpload
        engine.syncNow()
        backend.cutTheNetworkFor = null
        backend.seedAttachment(ServerAttachment(id = 50, entryId = 900, originalName = "facture.jpg"))

        after(15.minutes)
        engine.syncNow()

        assertEquals(2, posts(uploads), "the file on the server is somebody else's: this one is uploaded")
        assertEquals(listOf("bon-1.jpg", "facture.jpg"), backend.attachments.map { it.originalName }.sorted())
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, true), photo(photo)!!.let { listOf(it.syncStatus, it.serverId != 50L) })
    }

    @Test
    fun a_file_of_the_same_name_added_elsewhere_is_not_merged_into_a_file_that_was_never_sent() = runTest {
        aSyncedSite()
        connectivity.setOnline(false)
        val photo = aPhoto("bon-1.jpg")
        engine.syncNow()
        connectivity.setOnline(true)
        backend.seedAttachment(ServerAttachment(id = 50, entryId = 900, originalName = "bon-1.jpg"))
        backend.uploadsLeftBeforeRateLimit = 0

        engine.syncLog("l800")

        val rows = db.attachmentDao().findForEntry("e900")
        assertEquals(2, rows.size, "the file from elsewhere is downloaded as its own row")
        assertEquals(listOf<Any?>(SyncStatus.PENDING, null), photo(photo)!!.let { listOf(it.syncStatus, it.serverId) }, "this one was never sent: it still has to be")
        backend.uploadsLeftBeforeRateLimit = null
        after(1.minutes)
        engine.syncNow()
        assertEquals(2, backend.attachments.size, "both exist on the server")
        assertEquals(SyncStatus.SYNCED, photo(photo)!!.syncStatus)
    }

    @Test
    fun twenty_five_photos_added_at_once_all_leave_within_the_quota_without_loss_or_duplicate() = runTest {
        aSyncedSite()
        val photos = (1..25).map { aPhoto("bon-${it.toString().padStart(2, '0')}.jpg") }
        backend.uploadsLeftBeforeRateLimit = 20
        backend.rateLimitRetryAfterSeconds = 3
        backend.seed(ServerProject(id = 6, name = "Arrivé pendant l'attente"))

        assertEquals(SyncOutcome.Synced, engine.syncNow())

        assertEquals(listOf(21, 1), listOf(posts(uploads), backend.rateLimitedUploads), "twenty accepted, the twenty-first rate limited, the four others not tried")
        assertEquals(20, photos.count { photo(it)!!.syncStatus == SyncStatus.SYNCED })
        assertTrue(db.projectDao().findByServerId(6) != null, "the pull is done while the files wait")
        assertEquals(0, issues.observeIssueCount().first())
        assertEquals(5, unsent.countUnsynced())

        var passes = 1
        while (photos.any { photo(it)!!.syncStatus != SyncStatus.SYNCED } && passes < 20) {
            threePasses()
            after(3.seconds)
            backend.uploadsLeftBeforeRateLimit = 1
            assertEquals(SyncOutcome.Synced, engine.syncNow())
            passes++
        }

        assertEquals(setOf(SyncStatus.SYNCED), photos.map { photo(it)!!.syncStatus }.toSet(), "everything left")
        assertEquals(photos.size, backend.attachments.size, "nothing lost, nothing twice")
        assertEquals((1..25).map { "bon-${it.toString().padStart(2, '0')}.jpg" }, backend.attachments.map { it.originalName }.sorted())
        assertEquals(25, db.attachmentDao().findForEntry("e900").mapNotNull { it.serverId }.toSet().size)
        assertEquals(listOf(30, 5), listOf(posts(uploads), backend.rateLimitedUploads), "one upload per file, plus one refused each time the quota ran out")
        assertEquals(0, unsent.countUnsynced())
    }

    @Test
    fun after_the_process_dies_a_waiting_upload_is_tried_once_more_at_most() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon-1.jpg")
        theServerAnswersUploadsWith(HttpStatusCode.ServiceUnavailable)
        threePasses()

        val restarted = anEngine()
        repeat(3) { restarted.syncNow() }

        assertEquals(2, posts(uploads))
        assertStillWaitingWithoutAnyRefusal(photo, "after a restart")
    }
}
