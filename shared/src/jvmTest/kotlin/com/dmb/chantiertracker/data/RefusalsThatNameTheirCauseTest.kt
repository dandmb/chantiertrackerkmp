package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueParent
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.presentation.sync.reasonSentenceRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.sync_reason_file_refused
import com.dmb.chantiertracker.resources.sync_reason_image_too_large
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerRefusal
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RefusalsThatNameTheirCauseTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val fileStore = FakeAttachmentFileStore()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)

    private val engine = SyncEngine(
        dao = db.projectDao(), api = backend.api(), stageDao = db.stageDao(), stageApi = backend.stageApi(),
        materialDao = db.materialDao(), materialAdoptionDao = db.materialAdoptionDao(), materialApi = backend.materialApi(),
        dailyLogDao = db.dailyLogDao(), dailyEntryDao = db.dailyEntryDao(), dailyLogApi = backend.dailyLogApi(),
        purchaseLineDao = db.purchaseLineDao(), purchaseLineApi = backend.purchaseLineApi(),
        consumptionLineDao = db.consumptionLineDao(), consumptionLineApi = backend.consumptionLineApi(),
        attachmentDao = db.attachmentDao(), attachmentApi = backend.attachmentApi(), attachmentFileStore = fileStore,
        invitationDao = db.invitationDao(), invitationApi = backend.invitationApi(), accountApi = backend.accountApi(),
        stockApi = backend.stockApi(), stockDao = db.stockDao(), connectivity = connectivity, syncState = SyncStateHolder(),
        scope = AppCoroutineScope(), clock = MutableClock(1_800_000_000_000L), backgroundSync = FakeBackgroundSync(),
        awaitedServerVersions = db.syncIssueActionDao(),
    )
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest fun close() = db.close()

    private suspend fun aSyncedSite() {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 901, dailyLogId = 800, type = "WORK", summary = "Coulage"))
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e901", dailyLogLocalId = "l800", type = "WORK", summary = "Coulage", serverId = 901, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    @Test
    fun a_photo_refused_as_too_large_is_a_file_refusal_that_says_so() = runTest {
        aSyncedSite()
        val photo = fileStore.save(byteArrayOf(1, 2, 3), "panorama.jpg")
        db.attachmentDao().upsert(localAttachment("a-big", entryLocalId = "e901", localPath = photo, originalName = "panorama.jpg", sizeBytes = 3))
        backend.refuseWith = { request ->
            ServerRefusal(HttpStatusCode.BadRequest, "IMAGE_TOO_LARGE").takeIf { request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/attachments") }
        }

        engine.syncNow()

        val row = db.attachmentDao().findByLocalId("a-big")!!
        assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, SyncError.FILE_REFUSED, "IMAGE_TOO_LARGE"), listOf(row.syncStatus, row.lastSyncError, row.serverErrorCode))
        val listed = issues.observeIssues().first().single { it.localId == "a-big" }
        assertEquals(RefusalReason.FILE_REFUSED, listed.issue.reason)
        assertEquals(Res.string.sync_reason_image_too_large, listed.issue.reasonSentenceRes(), "« Photo trop grande (plus de 40 mégapixels). »")
    }

    @Test
    fun another_file_refusal_keeps_the_general_sentence() = runTest {
        aSyncedSite()
        val photo = fileStore.save(byteArrayOf(1, 2, 3), "photo.bmp")
        db.attachmentDao().upsert(localAttachment("a-bmp", entryLocalId = "e901", localPath = photo, originalName = "photo.bmp", sizeBytes = 3))
        backend.refuseWith = { request ->
            ServerRefusal(HttpStatusCode.BadRequest, "UNSUPPORTED_IMAGE").takeIf { request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/attachments") }
        }

        engine.syncNow()

        val listed = issues.observeIssues().first().single { it.localId == "a-bmp" }
        assertEquals(Res.string.sync_reason_file_refused, listed.issue.reasonSentenceRes())
    }

    @Test
    fun a_line_waiting_for_a_refused_entry_and_a_refused_material_names_both() = runTest {
        aSyncedSite()
        val pending = SyncStatus.PENDING
        db.dailyLogDao().upsert(localDailyLog("l-today", stageLocalId = "st90", serverId = null, date = "2026-09-06"))
        db.dailyEntryDao().upsert(localDailyEntry("e-purchase", dailyLogLocalId = "l-today", type = "PURCHASE", summary = null, serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.materialDao().upsert(localMaterial("m-sand", projectLocalId = "p5", name = "Sable", unit = "t", serverId = null, pendingOp = PendingOp.CREATE, syncStatus = pending))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-sand", entryLocalId = "e-purchase", materialLocalId = "m-sand", quantity = 2.0, unitPrice = 30.0))
        db.consumptionLineDao().upsert(localConsumptionLine("cl-sand", entryLocalId = "e901", materialLocalId = "m-sand", quantity = 1.0))
        backend.refuseWith = { request ->
            val path = request.url.encodedPath
            ServerRefusal(HttpStatusCode.Forbidden, "PROJECT_OR_STAGE_INACTIVE")
                .takeIf { request.method == HttpMethod.Post && (path.endsWith("/purchases") || path.endsWith("/materials")) }
        }

        engine.syncNow()

        val listed = issues.observeIssues().first()
        val purchaseLine = listed.single { it.localId == "pl-sand" }
        assertEquals(SyncIssueKind.BLOCKED_BY_PARENT, purchaseLine.issue.kind)
        assertEquals(SyncIssueParent(SyncIssueTarget.ENTRY, entryType = EntryType.PURCHASE, date = "2026-09-06"), purchaseLine.blockedBy)
        assertEquals(SyncIssueParent(SyncIssueTarget.MATERIAL, name = "Sable"), purchaseLine.alsoBlockedBy, "the refused material is named too")

        val consumptionLine = listed.single { it.localId == "cl-sand" }
        assertEquals(SyncIssueParent(SyncIssueTarget.MATERIAL, name = "Sable"), consumptionLine.blockedBy, "its entry is on the server: only the material")
        assertEquals(null, consumptionLine.alsoBlockedBy)
    }
}
