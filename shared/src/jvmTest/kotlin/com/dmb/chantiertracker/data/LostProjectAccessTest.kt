package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerMember
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LostProjectAccessTest {

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
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), backend.attachmentApi(), fileStore, savesWithoutStartingAPass, scope)
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        engine, db.syncIssueActionDao(), fileStore, connectivity,
    )

    @AfterTest fun close() = db.close()

    private suspend fun aSupervisorWithUnsentWorkOnVilla(): String {
        backend.seed(ServerProject(id = 5, name = "Villa", ownerId = 1))
        backend.seed(ServerProject(id = 8, name = "Hangar", ownerId = 1))
        backend.seedMembers(5, ServerMember(1, "Dan", "dan@x.dev", "ADMIN"), ServerMember(9, "Awa", "awa@x.dev", "SUPERVISOR"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        val villa = db.projectDao().findByServerId(5)!!.localId
        engine.syncProject(villa)
        val stage = db.stageDao().findForProject(villa).single().localId
        val entry = logs.createPurchaseEntry(stage, "2026-10-27")
        attachments.addAttachment(entry.entryLocalId, byteArrayOf(1, 2, 3), "recu.jpg", "image/jpeg")
        stages.createStage(CreateStageInput(projectLocalId = villa, name = "Toiture", description = null, estimatedBudget = null, startDate = null, endDate = null))
        assertTrue(db.localDataDao().countUnsynced() > 0)
        return villa
    }

    @Test
    fun a_member_removed_from_a_project_loses_it_locally_with_everything_still_unsent() = runTest {
        val villa = aSupervisorWithUnsentWorkOnVilla()
        backend.accessLostTo += 5L

        engine.syncNow()

        assertNull(db.projectDao().findByLocalId(villa), "the project is gone")
        assertEquals(emptyList(), db.stageDao().findForProject(villa))
        assertEquals(emptyList(), db.projectDao().observeMembers(villa).first())
        assertEquals(0, db.localDataDao().countUnsynced(), "nothing left waiting for a project out of reach")
        assertEquals(emptyList(), issues.observeIssues().first(), "no sync issue for writes that can never leave")
        assertNotNull(db.projectDao().findByServerId(8), "the other projects stay")
        assertIs<SyncOutcome.Synced>(engine.syncNow(), "the next pass runs clean")
    }

    @Test
    fun a_pending_edit_on_a_project_out_of_reach_is_dropped_with_the_project() = runTest {
        val villa = aSupervisorWithUnsentWorkOnVilla()
        projects.updateProject(
            villa,
            UpdateProjectInput("Villa B", null, null, "USD", "UTC", ProjectStatus.IN_PROGRESS),
        )
        backend.accessLostTo += 5L

        engine.syncNow()

        assertNull(db.projectDao().findByLocalId(villa))
        assertEquals(0, db.localDataDao().countUnsynced())
    }

    @Test
    fun opening_a_project_out_of_reach_drops_it_without_failing() = runTest {
        val villa = aSupervisorWithUnsentWorkOnVilla()
        backend.accessLostTo += 5L

        val outcome = engine.syncProject(villa)

        assertTrue(outcome !is SyncOutcome.Failed, "outcome: $outcome")
        assertNull(db.projectDao().findByLocalId(villa))
        assertEquals(0, db.localDataDao().countUnsynced())
    }
}
