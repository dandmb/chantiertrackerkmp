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
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.ServerEntry
import com.dmb.chantiertracker.support.ServerLog
import com.dmb.chantiertracker.support.ServerMaterial
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerPurchaseLine
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localAttachment
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

class RetryNeverResendsFrozenEntriesTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val backend = FakeProjectBackend()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val fileStore = FakeAttachmentFileStore()
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
        awaitedServerVersions = com.dmb.chantiertracker.support.NoAwaitedServerVersion,
        syncState = SyncStateHolder(),
        scope = AppCoroutineScope(),
    )
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(), engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest fun close() = db.close()

    private fun sent(method: String, pathSuffix: String) =
        backend.receivedMethods.count { it.startsWith("$method ") && it.endsWith(pathSuffix) }

    private suspend fun aSiteWithThreeFrozenRows() {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE"))
        backend.seedMaterial(ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        backend.seedPurchaseLine(ServerPurchaseLine(id = 5000, entryId = 900, materialId = 7, quantity = 12.0, unitPrice = 6.0))
        backend.seedPurchaseLine(ServerPurchaseLine(id = 5001, entryId = 900, materialId = 7, quantity = 4.0, unitPrice = 6.0))
        db.projectDao().upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st90", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.materialDao().upsert(localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.purchaseLineDao().upsert(
            localPurchaseLine("pl-consumed", entryLocalId = "e900", materialLocalId = "m7", quantity = 3.0, serverId = 5000, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED)
                .copy(lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "STOCK_CONSUMED", serverQuantity = 12.0),
        )
        db.purchaseLineDao().upsert(
            localPurchaseLine("pl-suspended", entryLocalId = "e900", materialLocalId = "m7", quantity = 9.0, serverId = 5001, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED)
                .copy(lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "PROJECT_OR_STAGE_INACTIVE", serverQuantity = 4.0),
        )
        val path = fileStore.save(ByteArray(8), "lourde.jpg")
        db.attachmentDao().upsert(
            localAttachment("a-heavy", entryLocalId = "e900", localPath = path)
                .copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.FILE_REFUSED, serverErrorCode = "ATTACHMENT_TOO_LARGE"),
        )
    }

    private suspend fun item(localId: String) = issues.observeIssues().first().single { it.localId == localId }

    private fun whatWasSent() = listOf(sent("PATCH", "/purchase-lines/5000"), sent("PATCH", "/purchase-lines/5001"), sent("POST", "/entries/900/attachments"))

    @Test
    fun retrying_one_refused_change_sends_it_once_and_never_the_other_frozen_entries() = runTest {
        aSiteWithThreeFrozenRows()
        backend.updateStatus = HttpStatusCode.Forbidden
        backend.updateRefusalCode = "PROJECT_OR_STAGE_INACTIVE"

        assertEquals(RetryOutcome.STILL_REFUSED, issues.retry(item("pl-suspended")))

        assertEquals(listOf(0, 1, 0), whatWasSent(), "only the retried change left; the consumed-stock change and the refused file stayed frozen")
        val again = db.purchaseLineDao().findByLocalId("pl-suspended")!!
        assertEquals(listOf(SyncStatus.CONFLICTED, SyncError.UPDATE_REFUSED, "PROJECT_OR_STAGE_INACTIVE"), listOf(again.syncStatus, again.lastSyncError, again.serverErrorCode))
        assertEquals(9.0, again.quantity, "what the user typed is kept")
    }

    @Test
    fun a_retry_still_refused_does_not_loop_the_next_passes_send_nothing() = runTest {
        aSiteWithThreeFrozenRows()
        backend.updateStatus = HttpStatusCode.Forbidden
        backend.updateRefusalCode = "PROJECT_OR_STAGE_INACTIVE"
        issues.retry(item("pl-suspended"))

        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(SyncOutcome.Synced, engine.syncNow())
        assertEquals(SyncOutcome.Synced, engine.syncLog("l800"))

        assertEquals(listOf(0, 1, 0), whatWasSent(), "one retry is one request; nothing is sent again at the following passes")
        assertEquals(3, issues.observeIssues().first().size, "the three entries are still listed")
    }

    @Test
    fun retrying_an_entry_frozen_for_a_reason_only_a_correction_can_lift_sends_nothing() = runTest {
        aSiteWithThreeFrozenRows()

        assertEquals(RetryOutcome.STILL_REFUSED, issues.retry(item("pl-consumed")))
        assertEquals(RetryOutcome.STILL_REFUSED, issues.retry(item("a-heavy")))

        assertEquals(listOf(0, 0, 0), whatWasSent())
        assertEquals(emptyList(), backend.receivedMethods.filter { !it.startsWith("GET ") }, "no write at all reached the server")
    }

    @Test
    fun once_the_refusal_is_lifted_the_retried_change_leaves_and_the_others_stay_frozen() = runTest {
        aSiteWithThreeFrozenRows()

        assertEquals(RetryOutcome.ACCEPTED, issues.retry(item("pl-suspended")))

        assertEquals(listOf(0, 1, 0), whatWasSent())
        assertEquals(SyncStatus.SYNCED, db.purchaseLineDao().findByLocalId("pl-suspended")!!.syncStatus)
        assertEquals(SyncError.UPDATE_REFUSED, db.purchaseLineDao().findByLocalId("pl-consumed")!!.lastSyncError)
        assertEquals(SyncError.FILE_REFUSED, db.attachmentDao().findByLocalId("a-heavy")!!.lastSyncError)
        assertEquals(
            listOf(SyncIssueKind.REFUSED, SyncIssueKind.UPDATE_REFUSED),
            issues.observeIssues().first().map { it.issue.kind }.sortedBy { it.ordinal },
        )
    }

    @Test
    fun a_retry_while_offline_sends_nothing_and_leaves_the_three_entries_frozen() = runTest {
        aSiteWithThreeFrozenRows()
        connectivity.setOnline(false)

        assertEquals(RetryOutcome.NOT_SENT, issues.retry(item("pl-suspended")))

        assertEquals(emptyList(), backend.receivedMethods)
        assertEquals(SyncError.UPDATE_REFUSED, db.purchaseLineDao().findByLocalId("pl-suspended")!!.lastSyncError)
        assertEquals(emptyList(), db.purchaseLineDao().findPending().map { it.localId }, "nothing is left in the queue for the next pass")
    }
}
