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
import com.dmb.chantiertracker.data.local.OrphanAttachmentFileCleaner
import com.dmb.chantiertracker.support.fakeUploadFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

class VideosGoThroughTheEngineTest {

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
    private val stages = StageRepositoryImpl(db.stageDao(), savesWithoutStartingAPass, scope)
    private val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), savesWithoutStartingAPass, scope)
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, engine, scope)
    private val addsWithoutStartingAPass = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, savesWithoutStartingAPass, scope)
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest
    fun close() {
        kotlinx.coroutines.runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
        db.close()
    }

    private val uploads = "/entries/900/attachments"
    private val rawVideo = ByteArray(6_000) { 4 }
    private val transcodedByTheServer = listOf<Byte>(9, 9, 9)
    private var nextWitness = 6_000L

    private suspend fun aSyncedSite() {
        backend.nextLogId = 2_000L
        backend.nextEntryId = 3_000L
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        val synced = SyncStatus.SYNCED
        val none = PendingOp.NONE
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
    }

    private suspend fun <T> inRealTime(block: suspend () -> T): T? = withContext(Dispatchers.Default) { withTimeoutOrNull(5_000) { block() } }

    private suspend fun aVideo(name: String = "visite.mov", onEntry: String = "e900"): String? =
        inRealTime { runCatching { addsWithoutStartingAPass.addVideo(onEntry, fakeUploadFile(rawVideo, name = name, mimeType = "video/quicktime")).localId }.getOrNull() }

    private suspend fun aPhoto(name: String): String = addsWithoutStartingAPass.addAttachment("e900", ByteArray(8), name, "image/jpeg").localId

    private suspend fun row(id: String?) = id?.let { db.attachmentDao().findByLocalId(it) }

    private fun stateOf(row: SyncedRow?) = row?.let { listOf(it.syncStatus, it.pendingOp, it.lastSyncError, it.serverErrorCode) }

    private val waitingToBeSent = listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, null)

    private fun posts(path: String = uploads) = backend.receivedMethods.count { it == "POST $path" }

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    private suspend fun aPass(with: SyncEngine = engine): SyncOutcome? = inRealTime { with.syncNow().also { with.awaitVideosBeingSent() } }

    private suspend fun threePasses() = repeat(3) { aPass() }

    private fun isAnUpload(request: HttpRequestData) = request.method.value == "POST" && request.url.encodedPath.endsWith("/attachments")

    private fun somethingArrivesOnTheServer(): Long = nextWitness++.also { backend.seed(ServerProject(id = it, name = "Arrivé pendant la passe $it")) }

    private suspend fun whatIsToReview() = issues.observeIssues().first().filter { it.issue.kind != SyncIssueKind.BLOCKED_BY_PARENT }.map { it.key }

    private suspend fun assertSentAndReplacedByWhatTheServerMade(id: String?, context: String) {
        val sent = row(id)
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, PendingOp.NONE, "video/mp4", 30), listOf(sent?.syncStatus, sent?.pendingOp, sent?.mimeType, sent?.durationSeconds), context)
        assertEquals(transcodedByTheServer, fileStore.readBytes(sent!!.localPath).toList(), "$context: the small file the server made is what the device keeps")
        assertEquals(setOf(sent.localPath), fileStore.storedPaths, "$context: the raw video is deleted once the server has it")
    }

    @Test
    fun a_video_added_offline_is_kept_and_leaves_when_the_network_is_back() = runTest {
        aSyncedSite()
        connectivity.setOnline(false)

        val video = aVideo()

        assertEquals(waitingToBeSent, stateOf(row(video)), "kept on the device, waiting")
        assertEquals(rawVideo.toList(), row(video)?.let { fileStore.readBytes(it.localPath).toList() })
        assertEquals(SyncOutcome.Skipped, aPass())
        assertEquals(0, posts())

        connectivity.setOnline(true)
        assertEquals(SyncOutcome.Synced, aPass())

        assertEquals(1, posts())
        assertSentAndReplacedByWhatTheServerMade(video, "back online")
        assertEquals(listOf("visite.mov"), backend.attachments.map { it.originalName })
    }

    @Test
    fun a_video_added_on_an_entry_made_offline_leaves_in_the_pass_that_creates_the_entry() = runTest {
        aSyncedSite()
        connectivity.setOnline(false)
        val entry = logs.createPurchaseEntry("st90", "2026-10-02").entryLocalId
        val video = aVideo(onEntry = entry)
        assertEquals(waitingToBeSent, stateOf(row(video)))

        connectivity.setOnline(true)
        assertEquals(SyncOutcome.Synced, aPass())

        assertSentAndReplacedByWhatTheServerMade(video, "the entry was created first")
    }

    @Test
    fun a_video_whose_upload_was_cut_by_the_death_of_the_process_leaves_at_the_next_start() = runTest {
        aSyncedSite()
        val video = aVideo()
        backend.cutTheNetworkFor = ::isAnUpload
        assertEquals(SyncOutcome.Synced, aPass())
        assertEquals(waitingToBeSent, stateOf(row(video)), "nothing was lost: the row and its file are still there")
        assertEquals(rawVideo.toList(), row(video)?.let { fileStore.readBytes(it.localPath).toList() })

        backend.cutTheNetworkFor = null
        val afterTheRestart = anEngine()
        assertEquals(SyncOutcome.Synced, aPass(afterTheRestart))

        assertEquals(2, posts())
        assertSentAndReplacedByWhatTheServerMade(video, "after the restart")
        assertEquals(1, backend.attachments.size)
    }

    @Test
    fun a_rate_limited_video_waits_for_the_time_the_server_gave_like_a_photo() = runTest {
        aSyncedSite()
        val video = aVideo()
        val photo = aPhoto("bon.jpg")
        backend.uploadsLeftBeforeRateLimit = 0
        backend.rateLimitRetryAfterSeconds = 30
        val witness = somethingArrivesOnTheServer()

        assertEquals(SyncOutcome.Synced, aPass(), "a rate limit does not fail the pass")

        assertTrue(db.projectDao().findByServerId(witness) != null, "the pull is done")
        assertEquals(listOf(waitingToBeSent, waitingToBeSent), listOf(stateOf(row(video)), stateOf(row(photo))), "a rate limit is not a refusal")
        assertEquals(emptyList(), whatIsToReview())
        threePasses()
        assertEquals(1, posts(), "the quota is per account: the photo was refused, the video is not even tried")

        backend.uploadsLeftBeforeRateLimit = 5
        after(30.seconds - 1.milliseconds)
        threePasses()
        assertEquals(1, posts(), "not before the time the server gave")
        after(1.milliseconds)
        assertEquals(SyncOutcome.Synced, aPass())

        assertEquals(3, posts())
        assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.SYNCED), listOf(row(video)?.syncStatus, row(photo)?.syncStatus))
    }

    @Test
    fun a_rate_limit_on_the_video_itself_makes_it_wait_and_is_not_a_step_of_the_spacing() = runTest {
        aSyncedSite()
        val video = aVideo()
        backend.uploadsLeftBeforeRateLimit = 0
        backend.rateLimitRetryAfterSeconds = 30

        assertEquals(SyncOutcome.Synced, aPass())
        threePasses()
        assertEquals(1, posts())
        assertEquals(waitingToBeSent, stateOf(row(video)))

        backend.uploadsLeftBeforeRateLimit = null
        backend.refuseWith = { request -> if (isAnUpload(request)) ServerRefusal(HttpStatusCode.BadGateway, null) else null }
        after(30.seconds)
        aPass()
        assertEquals(2, posts())
        backend.refuseWith = null
        after(15.minutes - 1.milliseconds)
        threePasses()
        assertEquals(2, posts())
        after(1.milliseconds)
        aPass()

        assertEquals(3, posts(), "a server error after a rate limit starts the spacing at fifteen minutes")
        assertSentAndReplacedByWhatTheServerMade(video, "in the end")
    }

    @Test
    fun a_video_the_server_could_not_answer_keeps_waiting_and_is_spaced_like_a_photo() = runTest {
        aSyncedSite()
        val failures: List<Pair<String, () -> Unit>> = listOf(
            "500" to { backend.refuseWith = { request -> if (isAnUpload(request)) ServerRefusal(HttpStatusCode.InternalServerError, null) else null } },
            "503" to { backend.refuseWith = { request -> if (isAnUpload(request)) ServerRefusal(HttpStatusCode.ServiceUnavailable, null) else null } },
            "cut while sending" to { backend.cutTheNetworkFor = ::isAnUpload },
        )
        for ((failure, fail) in failures) {
            val video = aVideo("visite-$failure.mov")
            val before = posts()
            val witness = somethingArrivesOnTheServer()
            fail()

            assertEquals(SyncOutcome.Synced, aPass(), "$failure: the pass goes on")

            assertTrue(db.projectDao().findByServerId(witness) != null, "$failure: the pull is done")
            assertEquals(waitingToBeSent, stateOf(row(video)), "$failure: still waiting, no refusal written")
            assertEquals(emptyList(), whatIsToReview(), "$failure: nothing to review")
            threePasses()
            assertEquals(1, posts() - before, "$failure: not sent again at every pass")
            after(15.minutes - 1.milliseconds)
            threePasses()
            assertEquals(1, posts() - before, "$failure: not before fifteen minutes")
            after(1.milliseconds)
            aPass()
            assertEquals(2, posts() - before, "$failure: tried again fifteen minutes later")
            assertEquals(rawVideo.toList(), row(video)?.let { fileStore.readBytes(it.localPath).toList() }, "$failure: the raw video is still on the device")

            backend.refuseWith = null
            backend.cutTheNetworkFor = null
            after(1.hours)
            aPass()
            assertEquals(3, posts() - before, failure)
            assertEquals(SyncStatus.SYNCED, row(video)?.syncStatus, failure)
        }
    }

    @Test
    fun a_refused_video_is_classified_like_a_refused_photo() = runTest {
        aSyncedSite()
        val overThePlan = aVideo("formule.mov")
        backend.attachmentUploadRejection = HttpStatusCode.Forbidden to "Limite atteinte."
        backend.attachmentUploadRejectionCode = "PLAN_LIMIT_EXCEEDED"

        assertEquals(SyncOutcome.Synced, aPass())
        threePasses()

        assertEquals(1, posts(), "a plan limit may lift: spaced, not sent at every pass")
        assertEquals(listOf("ATTACHMENT:$overThePlan"), whatIsToReview())
        assertEquals(rawVideo.toList(), row(overThePlan)?.let { fileStore.readBytes(it.localPath).toList() }, "the raw video is kept while the refusal may lift")
        after(15.minutes)
        backend.attachmentUploadRejection = null
        aPass()
        assertSentAndReplacedByWhatTheServerMade(overThePlan, "the plan limit was lifted")

        val tooLong = aVideo("longue.mov")
        backend.attachmentUploadRejection = HttpStatusCode.BadRequest to "Vidéo trop longue."
        backend.attachmentUploadRejectionCode = "VIDEO_TOO_LONG"
        aPass()
        after(24.hours)
        threePasses()

        assertEquals(3, posts(), "a file the server refuses for itself is frozen: sent once")
        assertEquals<Any?>(listOf(SyncStatus.CONFLICTED, SyncError.FILE_REFUSED, "VIDEO_TOO_LONG"), row(tooLong)?.let { listOf(it.syncStatus, it.lastSyncError, it.serverErrorCode) })
    }

    @Test
    fun the_thumbnail_shows_the_progress_of_the_video_being_sent_and_nothing_once_it_is_sent() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon.jpg")
        val video = aVideo()
        val seenBeforeTheServerReadIt = mutableListOf<Map<String, Float>>()
        val seenOnceTheServerHasItAll = mutableListOf<Float?>()
        backend.beforeHandle = { request -> if (isAnUpload(request)) seenBeforeTheServerReadIt += engine.uploadProgress.value }
        backend.holdTheAnswerOf = { request ->
            if (isAnUpload(request) && engine.uploadProgress.value.isNotEmpty()) {
                seenOnceTheServerHasItAll += attachments.observeAttachments("e900").first().single { it.localId == video }.uploadProgress
            }
        }

        assertEquals(SyncOutcome.Synced, aPass())

        assertEquals<Any?>(listOf(emptyMap(), mapOf(video to 0f)), seenBeforeTheServerReadIt, "nothing for the photo, the video starts at zero")
        assertEquals(listOf<Float?>(1f), seenOnceTheServerHasItAll, "every byte was sent")
        assertEquals(emptyMap(), engine.uploadProgress.value, "nothing left to show once sent")
        assertEquals(listOf<Float?>(null, null), attachments.observeAttachments("e900").first().map { it.uploadProgress })
        assertEquals(SyncStatus.SYNCED, row(photo)?.syncStatus)
    }

    @Test
    fun a_long_video_does_not_hold_back_the_other_rows_the_pull_or_the_next_passes() = runTest {
        aSyncedSite()
        val theServerIsReceiving = CompletableDeferred<Unit>()
        val letTheUploadEnd = CompletableDeferred<Unit>()
        backend.beforeHandle = { request ->
            if (isAnUpload(request)) {
                theServerIsReceiving.complete(Unit)
                letTheUploadEnd.await()
            }
        }
        val video = aVideo()
        assertEquals(waitingToBeSent, stateOf(row(video)))
        val stage = stages.createStage(CreateStageInput("p5", "Toiture", null, null, null, null))
        val witness = somethingArrivesOnTheServer()

        val pass = inRealTime { engine.syncNow() }

        assertEquals<Any?>(SyncOutcome.Synced, pass, "the pass ends without waiting for the video")
        assertTrue(inRealTime { theServerIsReceiving.await() } != null, "the video is on its way")
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(stage)!!.syncStatus, "the other rows are sent")
        assertTrue(db.projectDao().findByServerId(witness) != null, "the pull is done")
        assertEquals(waitingToBeSent, stateOf(row(video)))

        val anotherStage = stages.createStage(CreateStageInput("p5", "Peinture", null, null, null, null))
        assertEquals<Any?>(SyncOutcome.Synced, inRealTime { engine.syncNow() }, "another pass runs while the video is still being sent")
        assertEquals(SyncStatus.SYNCED, db.stageDao().findByLocalId(anotherStage)!!.syncStatus)
        assertEquals(1, posts(), "and it does not send the video a second time")

        letTheUploadEnd.complete(Unit)
        inRealTime { engine.awaitVideosBeingSent() }
        assertEquals(1, posts())
        assertSentAndReplacedByWhatTheServerMade(video, "once the upload ended")
    }

    @Test
    fun what_must_run_alone_stops_the_video_being_sent_and_the_video_leaves_later() = runTest {
        aSyncedSite()
        val theServerIsReceiving = CompletableDeferred<Unit>()
        backend.beforeHandle = { request ->
            if (isAnUpload(request)) {
                theServerIsReceiving.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        val video = aVideo()
        inRealTime { engine.syncNow() }
        assertTrue(inRealTime { theServerIsReceiving.await() } != null, "the video is on its way")

        val ranAlone = inRealTime { engine.runExclusive { engine.uploadProgress.value } }

        assertEquals<Any?>(emptyMap<String, Float>(), ranAlone, "signing out or switching account never waits for a video, and nothing is sent meanwhile")
        assertEquals(waitingToBeSent, stateOf(row(video)), "the video is not lost")
        backend.beforeHandle = null
        assertEquals(SyncOutcome.Synced, aPass())
        assertSentAndReplacedByWhatTheServerMade(video, "at the next pass")
        assertEquals(1, backend.attachments.size)
    }

    @Test
    fun a_video_the_server_stored_but_whose_answer_was_lost_is_recognised_by_its_name_and_not_sent_twice() = runTest {
        aSyncedSite()
        val video = aVideo()
        backend.loseTheAnswerOf = ::isAnUpload
        assertEquals(SyncOutcome.Synced, aPass())
        assertEquals(waitingToBeSent, stateOf(row(video)))
        assertEquals(1, backend.attachments.size, "the server has it")

        backend.loseTheAnswerOf = null
        after(15.minutes)
        assertEquals(SyncOutcome.Synced, aPass())

        assertEquals(1, posts(), "not sent a second time")
        assertEquals(1, backend.attachments.size)
        assertEquals(backend.attachments.single().id, row(video)?.serverId)
        assertSentAndReplacedByWhatTheServerMade(video, "recognised")
    }

    @Test
    fun the_pull_of_the_day_recognises_a_video_whose_answer_was_lost() = runTest {
        aSyncedSite()
        val video = aVideo()
        backend.loseTheAnswerOf = ::isAnUpload
        aPass()
        backend.loseTheAnswerOf = null

        assertEquals<Any?>(SyncOutcome.Synced, inRealTime { engine.syncLog("l800") })

        assertEquals(listOf(video), db.attachmentDao().findForEntry("e900").map { it.localId }, "one row on the device, the one the user added")
        assertSentAndReplacedByWhatTheServerMade(video, "recognised by the pull")
        threePasses()
        assertEquals(1, posts())
    }

    @Test
    fun a_pull_that_runs_while_a_video_is_being_sent_does_not_take_it_for_a_file_from_elsewhere() = runTest {
        aSyncedSite()
        val theServerStoredIt = CompletableDeferred<Unit>()
        val letTheAnswerLeave = CompletableDeferred<Unit>()
        backend.holdTheAnswerOf = { request ->
            if (isAnUpload(request)) {
                theServerStoredIt.complete(Unit)
                letTheAnswerLeave.await()
            }
        }
        val video = aVideo()
        inRealTime { engine.syncNow() }
        assertTrue(inRealTime { theServerStoredIt.await() } != null, "the server has the video, its answer is not back yet")

        assertEquals<Any?>(SyncOutcome.Synced, inRealTime { engine.syncLog("l800") })

        assertEquals(listOf(video), db.attachmentDao().findForEntry("e900").map { it.localId }, "no second row for the same video")
        letTheAnswerLeave.complete(Unit)
        inRealTime { engine.awaitVideosBeingSent() }
        assertEquals(listOf(video), db.attachmentDao().findForEntry("e900").map { it.localId })
        assertSentAndReplacedByWhatTheServerMade(video, "once the answer came")
    }

    @Test
    fun a_video_deleted_while_it_is_being_sent_is_removed_from_the_server_and_leaves_no_file() = runTest {
        aSyncedSite()
        val theServerStoredIt = CompletableDeferred<Unit>()
        val letTheAnswerLeave = CompletableDeferred<Unit>()
        backend.holdTheAnswerOf = { request ->
            if (isAnUpload(request)) {
                theServerStoredIt.complete(Unit)
                letTheAnswerLeave.await()
            }
        }
        val video = aVideo()
        inRealTime { engine.syncNow() }
        assertTrue(inRealTime { theServerStoredIt.await() } != null && video != null, "the video is being sent")

        addsWithoutStartingAPass.deleteAttachment(video!!)
        letTheAnswerLeave.complete(Unit)
        inRealTime { engine.awaitVideosBeingSent() }

        assertEquals(listOf<Any>(emptyList<String>(), emptyList<Long>(), emptySet<String>()), listOf(db.attachmentDao().findForEntry("e900").map { it.localId }, backend.attachments.map { it.id }, fileStore.storedPaths))
    }

    @Test
    fun a_video_sent_whose_small_copy_could_not_be_fetched_stays_playable_and_is_not_sent_again() = runTest {
        aSyncedSite()
        val video = aVideo()
        backend.refuseWith = { request -> if (request.method.value == "GET" && Regex("/attachments/\\d+$").containsMatchIn(request.url.encodedPath)) ServerRefusal(HttpStatusCode.InternalServerError, null) else null }

        assertEquals(SyncOutcome.Synced, aPass())
        threePasses()

        assertEquals(1, posts())
        assertEquals(SyncStatus.SYNCED, row(video)?.syncStatus)
        assertEquals(rawVideo.toList(), row(video)?.let { fileStore.readBytes(it.localPath).toList() }, "what the user filmed stays on the device")
    }

    @Test
    fun a_photo_is_sent_as_a_stream_too() = runTest {
        aSyncedSite()
        val photo = aPhoto("bon.jpg")

        assertEquals(SyncOutcome.Synced, aPass())

        assertEquals(SyncStatus.SYNCED, row(photo)?.syncStatus)
        assertEquals(listOf<Any>(0, listOf(row(photo)!!.localPath)), listOf<Any>(fileStore.readBytesCount, fileStore.sourcesOpened.distinct()), "the engine never reads a whole file into memory")
    }

    @Test
    fun the_cleaner_removes_a_copy_that_was_cut_after_ten_minutes_and_never_a_video_waiting_to_be_sent() = runTest {
        aSyncedSite()
        connectivity.setOnline(false)
        var now = 1_000_000L
        fileStore.now = now
        val waiting = aVideo()
        val cut = object : com.dmb.chantiertracker.domain.model.UploadFile {
            override val name = "coupee.mov"
            override val mimeType = "video/quicktime"
            override fun size(): Long = 10_000
            override fun openSource(): kotlinx.io.RawSource = object : kotlinx.io.RawSource {
                private var given = false
                override fun readAtMostTo(sink: kotlinx.io.Buffer, byteCount: Long): Long {
                    if (given) throw kotlinx.io.IOException("the process died during the copy")
                    given = true
                    sink.write(ByteArray(900) { 7 })
                    return 900
                }
                override fun close() = Unit
            }
        }
        inRealTime { runCatching { addsWithoutStartingAPass.addVideo("e900", cut) } }
        val kept = row(waiting)?.localPath
        val half = (fileStore.storedPaths - setOfNotNull(kept)).singleOrNull()
        assertEquals<Any?>(900, half?.let { fileStore.readBytes(it).size }, "half a copy is on the device, no row refers to it")
        val cleaner = OrphanAttachmentFileCleaner(fileStore, db.attachmentDao(), engine, scope, clock = { now })

        now += 10 * 60_000L - 1
        assertEquals(emptyList(), cleaner.removeOrphans(), "a copy may still be running: not before ten minutes")
        now += 1
        assertEquals(listOfNotNull(half), cleaner.removeOrphans())
        now += 30L * 24 * 60 * 60_000
        assertEquals(emptyList(), cleaner.removeOrphans())

        assertEquals(setOfNotNull(kept), fileStore.storedPaths, "the video waiting to be sent is never an orphan, however long it waits")
        assertEquals(waitingToBeSent, stateOf(row(waiting)))
    }
}
