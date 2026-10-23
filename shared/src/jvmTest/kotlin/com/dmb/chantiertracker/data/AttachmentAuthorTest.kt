package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
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
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentAuthorTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val fileStore = FakeAttachmentFileStore()
    private val scope = AppCoroutineScope()

    private val engine = SyncEngine(
        dao = db.projectDao(), api = backend.api(), stageDao = db.stageDao(), stageApi = backend.stageApi(),
        materialDao = db.materialDao(), materialAdoptionDao = db.materialAdoptionDao(), materialApi = backend.materialApi(),
        dailyLogDao = db.dailyLogDao(), dailyEntryDao = db.dailyEntryDao(), dailyLogApi = backend.dailyLogApi(),
        purchaseLineDao = db.purchaseLineDao(), purchaseLineApi = backend.purchaseLineApi(),
        consumptionLineDao = db.consumptionLineDao(), consumptionLineApi = backend.consumptionLineApi(),
        attachmentDao = db.attachmentDao(), attachmentApi = backend.attachmentApi(), attachmentFileStore = fileStore,
        invitationDao = db.invitationDao(), invitationApi = backend.invitationApi(), accountApi = backend.accountApi(),
        stockApi = backend.stockApi(), stockDao = db.stockDao(), connectivity = FakeConnectivityObserver(initiallyOnline = true),
        syncState = SyncStateHolder(), scope = scope, clock = MutableClock(1_800_000_000_000L), backgroundSync = FakeBackgroundSync(),
        awaitedServerVersions = db.syncIssueActionDao(),
    )
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, FakeSyncer(), scope)

    @AfterTest fun close() = db.close()

    private suspend fun aSyncedDay() {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "Livraison"))
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    @Test
    fun an_attachment_pulled_from_the_server_keeps_its_author() = runTest {
        aSyncedDay()
        backend.seedAttachment(ServerAttachment(id = 60, entryId = 900, originalName = "proprietaire.jpg", uploadedById = 1L))
        backend.seedAttachment(ServerAttachment(id = 61, entryId = 900, originalName = "superviseur.jpg", uploadedById = 7L))

        engine.syncLog("l800")

        val byName = db.attachmentDao().findForEntry("e900").associateBy { it.originalName }
        assertEquals(1L, byName.getValue("proprietaire.jpg").uploadedById)
        assertEquals(7L, byName.getValue("superviseur.jpg").uploadedById)
        val shown = attachments.observeAttachments("e900").first().associateBy { it.originalName }
        assertEquals(listOf<Any?>(7L, true), shown.getValue("superviseur.jpg").let { listOf(it.uploadedById, it.sentToTheServer) })
    }

    @Test
    fun an_attachment_from_before_v17_gets_its_author_at_the_next_sync_and_nothing_else_changes() = runTest {
        aSyncedDay()
        backend.seedAttachment(ServerAttachment(id = 60, entryId = 900, originalName = "dalle.jpg", uploadedById = 7L))
        val photo = fileStore.save(byteArrayOf(1, 2, 3), "dalle.jpg")
        val migrated = localAttachment("a-old", entryLocalId = "e900", localPath = photo, originalName = "dalle.jpg", sizeBytes = 3, serverId = 60, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)
        db.attachmentDao().upsert(migrated)

        engine.syncLog("l800")

        val after = db.attachmentDao().findByLocalId("a-old")!!
        assertEquals(7L, after.uploadedById, "filled from the server")
        assertEquals(migrated.copy(uploadedById = 7L), after.copy(localVersion = migrated.localVersion), "nothing else changes")
        assertEquals(1, db.attachmentDao().findForEntry("e900").size, "not downloaded a second time")
    }

    @Test
    fun an_attachment_sent_from_here_takes_its_author_from_the_answer_of_the_server() = runTest {
        aSyncedDay()
        backend.uploaderId = 7L
        val added = attachments.addAttachment("e900", byteArrayOf(4, 5, 6), "fissure.jpg", "image/jpeg")
        assertEquals(listOf<Any?>(null, false), attachments.observeAttachments("e900").first().single().let { listOf(it.uploadedById, it.sentToTheServer) }, "not on the server yet")

        engine.syncNow()

        assertEquals(7L, db.attachmentDao().findByLocalId(added.localId)?.uploadedById)
        assertEquals(true, attachments.observeAttachments("e900").first().single().sentToTheServer)
    }
}
