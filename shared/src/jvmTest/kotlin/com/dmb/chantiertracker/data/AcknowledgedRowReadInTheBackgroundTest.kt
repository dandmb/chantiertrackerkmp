package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The pass that « J'ai compris » requests runs on the app's own scope, whenever a thread is free.
// Here that scope is held until the test lets it go, so the order of the background pass and of
// the user's edit is chosen, not left to the load of the machine.
class AcknowledgedRowReadInTheBackgroundTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val fileStore = FakeAttachmentFileStore()
    private val executor = Executors.newSingleThreadExecutor()
    private val gate = CountDownLatch(1)
    private val heldScope = AppCoroutineScope(executor.asCoroutineDispatcher())

    init {
        executor.execute { gate.await() }
    }

    private val engine = SyncEngine(
        dao = db.projectDao(), api = backend.api(), stageDao = db.stageDao(), stageApi = backend.stageApi(),
        materialDao = db.materialDao(), materialAdoptionDao = db.materialAdoptionDao(), materialApi = backend.materialApi(),
        dailyLogDao = db.dailyLogDao(), dailyEntryDao = db.dailyEntryDao(), dailyLogApi = backend.dailyLogApi(),
        purchaseLineDao = db.purchaseLineDao(), purchaseLineApi = backend.purchaseLineApi(),
        consumptionLineDao = db.consumptionLineDao(), consumptionLineApi = backend.consumptionLineApi(),
        attachmentDao = db.attachmentDao(), attachmentApi = backend.attachmentApi(), attachmentFileStore = fileStore,
        invitationDao = db.invitationDao(), invitationApi = backend.invitationApi(), accountApi = backend.accountApi(),
        stockApi = backend.stockApi(), stockDao = db.stockDao(), connectivity = connectivity, syncState = SyncStateHolder(),
        scope = heldScope, backgroundSync = FakeBackgroundSync(), awaitedServerVersions = db.syncIssueActionDao(),
    )
    private val stages = StageRepositoryImpl(db.stageDao(), FakeSyncer(), heldScope)
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest
    fun close() {
        gate.countDown()
        executor.shutdownNow()
        db.close()
    }

    private val renamed = "Renommée pendant la lecture"
    private var renameHappened = false

    private fun readsOfTheStage() = backend.receivedMethods.count { it == "GET /stages/90" }

    private suspend fun aStageWhoseRefusedDeleteIsAcknowledgedOffline() {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(
            localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)
                .copy(lastSyncError = SyncError.REJECTED, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"),
        )
        connectivity.setOnline(false)
        issues.acknowledge(issues.observeIssues().first().single { it.localId == "st90" })
    }

    private fun renameWhileTheServerReadsTheStage() {
        backend.beforeHandle = { request ->
            if (!renameHappened && request.method.value == "GET" && request.url.encodedPath.endsWith("/stages/90")) {
                renameHappened = true
                stages.updateStage("st90", UpdateStageInput(renamed, null, null, null, null, StageStatus.IN_PROGRESS))
            }
        }
    }

    // Once the gate opens, the queued pass runs first on the single thread and takes the engine's
    // lock before it can suspend; a marker queued behind it proves that, then the lock is awaited.
    private suspend fun letTheBackgroundPassRunToItsEnd() {
        gate.countDown()
        val reached = CompletableDeferred<Unit>()
        executor.execute { reached.complete(Unit) }
        reached.await()
        engine.runExclusive { }
    }

    private suspend fun theRenameIsKeptAndSent() {
        backend.beforeHandle = null
        engine.syncNow()
        assertTrue(renameHappened, "the stage was renamed while the server was read")
        assertEquals(renamed, db.stageDao().findByLocalId("st90")!!.name, "the server version did not overwrite the rename")
        assertEquals(listOf(renamed), backend.stages.map { it.name }, "and the rename reaches the server")
    }

    @Test
    fun a_stage_renamed_while_the_background_pass_of_the_acknowledgement_reads_it_keeps_the_new_name() = runBlocking {
        aStageWhoseRefusedDeleteIsAcknowledgedOffline()
        renameWhileTheServerReadsTheStage()
        connectivity.setOnline(true)
        letTheBackgroundPassRunToItsEnd()

        assertEquals(1, readsOfTheStage(), "the background pass is the one that read the stage")
        theRenameIsKeptAndSent()
    }

    @Test
    fun a_stage_renamed_while_the_next_pass_reads_it_keeps_the_new_name_when_the_background_pass_ran_offline() = runBlocking {
        aStageWhoseRefusedDeleteIsAcknowledgedOffline()
        letTheBackgroundPassRunToItsEnd()
        assertEquals(0, readsOfTheStage(), "offline, the background pass read nothing")
        renameWhileTheServerReadsTheStage()
        connectivity.setOnline(true)

        engine.syncNow()

        assertEquals(1, readsOfTheStage())
        theRenameIsKeptAndSent()
    }
}
