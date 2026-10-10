package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.presentation.sync.SyncState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeStageDao
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerMember
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.ServerStage
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import com.dmb.chantiertracker.support.serverMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private class Fixture(
        val dao: FakeProjectDao = FakeProjectDao(),
        val stageDao: FakeStageDao = FakeStageDao(),
        val materialDao: com.dmb.chantiertracker.support.FakeMaterialDao = com.dmb.chantiertracker.support.FakeMaterialDao(),
        val dailyLogDao: com.dmb.chantiertracker.support.FakeDailyLogDao = com.dmb.chantiertracker.support.FakeDailyLogDao(),
        val dailyEntryDao: com.dmb.chantiertracker.support.FakeDailyEntryDao = com.dmb.chantiertracker.support.FakeDailyEntryDao(),
        val purchaseLineDao: com.dmb.chantiertracker.support.FakePurchaseLineDao = com.dmb.chantiertracker.support.FakePurchaseLineDao(),
        val consumptionLineDao: com.dmb.chantiertracker.support.FakeConsumptionLineDao = com.dmb.chantiertracker.support.FakeConsumptionLineDao(),
        val attachmentDao: com.dmb.chantiertracker.support.FakeAttachmentDao = com.dmb.chantiertracker.support.FakeAttachmentDao(),
        val fileStore: com.dmb.chantiertracker.support.FakeAttachmentFileStore = com.dmb.chantiertracker.support.FakeAttachmentFileStore(),
        val invitationDao: com.dmb.chantiertracker.support.FakeInvitationDao = com.dmb.chantiertracker.support.FakeInvitationDao(),
        val stockDao: com.dmb.chantiertracker.support.FakeStockDao = com.dmb.chantiertracker.support.FakeStockDao(purchaseLineDao, consumptionLineDao),
        val materialAdoptionDao: com.dmb.chantiertracker.support.FakeMaterialAdoptionDao =
            com.dmb.chantiertracker.support.FakeMaterialAdoptionDao(materialDao, purchaseLineDao, consumptionLineDao),
        val backend: FakeProjectBackend = FakeProjectBackend(),
        val connectivity: FakeConnectivityObserver = FakeConnectivityObserver(),
        val clock: MutableClock = MutableClock(serverMillis("2026-09-02T09:00:00")),
        val syncState: SyncStateHolder = SyncStateHolder(),
        val backgroundSync: FakeBackgroundSync = FakeBackgroundSync(),
    ) {
        var idSeq = 0
        val logged = mutableListOf<String>()
        fun engine(scope: CoroutineScope, catchUpInterval: Duration = 15.minutes) = SyncEngine(
            dao = dao,
            api = backend.api(),
            stageDao = stageDao,
            stageApi = backend.stageApi(),
            materialDao = materialDao,
            materialAdoptionDao = materialAdoptionDao,
            materialApi = backend.materialApi(),
            dailyLogDao = dailyLogDao,
            dailyEntryDao = dailyEntryDao,
            dailyLogApi = backend.dailyLogApi(),
            purchaseLineDao = purchaseLineDao,
            purchaseLineApi = backend.purchaseLineApi(),
            consumptionLineDao = consumptionLineDao,
            consumptionLineApi = backend.consumptionLineApi(),
            attachmentDao = attachmentDao,
            attachmentApi = backend.attachmentApi(),
            attachmentFileStore = fileStore,
            invitationDao = invitationDao,
            invitationApi = backend.invitationApi(),
            stockApi = backend.stockApi(),
            stockDao = stockDao,
            connectivity = connectivity,
            awaitedServerVersions = com.dmb.chantiertracker.support.NoAwaitedServerVersion,
            syncState = syncState,
            scope = scope,
            clock = clock,
            newLocalId = { "pulled-${idSeq++}" },
            backgroundSync = backgroundSync,
            catchUpInterval = catchUpInterval,
            log = { logged += it },
        )
    }

    // ─── offline write, then sync on reconnect ───────────────────────────────

    @Test
    fun offline_create_is_kept_local_and_pushed_once_connectivity_returns() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        val engine = f.engine(backgroundScope)

        f.dao.upsert(localProject("p1", name = "Villa Vidal", pendingOp = PendingOp.CREATE))

        assertIs<SyncOutcome.Skipped>(engine.syncNow())
        assertTrue(f.backend.receivedMethods.isEmpty(), "no network call while offline")
        assertEquals(SyncState.Offline, f.syncState.state.value)
        assertEquals(listOf("Villa Vidal"), f.dao.observeProjects().first().map { it.name })

        f.connectivity.setOnline(true)
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val stored = f.dao.findByLocalId("p1")!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertEquals(PendingOp.NONE, stored.pendingOp)
        assertNotNull(stored.serverId)
        assertEquals(listOf("Villa Vidal"), f.backend.projects.map { it.name })
        assertEquals(SyncState.Idle, f.syncState.state.value)
    }

    @Test
    fun reads_come_from_room_with_zero_connectivity_and_never_touch_the_network() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsertAll(
            listOf(
                localProject("a", name = "Alpha", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localProject("b", name = "Beta", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
            ),
        )
        val engine = f.engine(backgroundScope)

        assertEquals(setOf("Alpha", "Beta"), f.dao.observeProjects().first().map { it.name }.toSet())
        assertEquals("Alpha", f.dao.observeProject("a").first()?.name)
        assertIs<SyncOutcome.Skipped>(engine.syncNow())
        assertTrue(f.backend.receivedMethods.isEmpty())
    }

    // ─── optimistic-concurrency conflict resolution (ADR-21) ────────────────
    // A pending local edit is pushed as-is unless the server's `updatedAt` moved
    // since our last sync of that row — then the server version wins. No device
    // clock is involved, so a server running a non-UTC zone can't spuriously
    // "win" and silently drop a legitimate offline edit.

    @Test
    fun pull_lets_the_server_win_when_its_row_changed_since_our_last_sync() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Server name", updatedAt = "2026-09-05T10:00:00"))
        f.dao.upsert(
            localProject(
                "p5", name = "Stale local edit", serverId = 5,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                remoteUpdatedAt = serverMillis("2026-09-02T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByServerId(5)!!
        assertEquals("Server name", row.name, "server row moved since our sync → it wins")
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertFalse(f.backend.receivedMethods.any { it.startsWith("PATCH") }, "no push of the losing local edit")
    }

    @Test
    fun push_applies_the_local_edit_when_the_server_row_is_unchanged_since_our_sync() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Old server name", updatedAt = "2026-09-01T10:00:00"))
        f.backend.patchAppliedAt = "2026-09-04T00:00:00"
        f.dao.upsert(
            localProject(
                "p7", name = "Fresh local edit", serverId = 7,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                // We last synced this row while the server said 2026-09-01T10:00:00, and it
                // hasn't moved since — so our edit is on a fresh base and pushes cleanly.
                remoteUpdatedAt = serverMillis("2026-09-01T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertEquals("Fresh local edit", f.backend.projects.single { it.id == 7L }.name)
        assertEquals(SyncStatus.SYNCED, f.dao.findByServerId(7)!!.syncStatus)
    }

    @Test
    fun local_edit_is_dropped_regardless_of_wall_clock_when_the_server_is_ahead() = runTest {
        // Regression: the server serialises `updatedAt` in a non-UTC zone, so parsed
        // as UTC it looks hours "ahead" of the device clock — but it has NOT changed
        // since our sync, so the local edit must still be pushed, not silently lost.
        val f = Fixture(clock = MutableClock(serverMillis("2026-09-02T09:00:00")))
        f.backend.seed(ServerProject(id = 8, name = "Server", updatedAt = "2026-09-02T11:00:00"))
        f.backend.patchAppliedAt = "2026-09-02T11:30:00"
        f.dao.upsert(
            localProject(
                "p8", name = "Legit offline edit", serverId = 8,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                remoteUpdatedAt = serverMillis("2026-09-02T11:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertEquals("Legit offline edit", f.backend.projects.single { it.id == 8L }.name)
        assertEquals(SyncStatus.SYNCED, f.dao.findByServerId(8)!!.syncStatus)
    }

    @Test
    fun two_concurrent_edits_converge_on_the_first_writer() = runTest {
        // One server, two devices that both synced the same base row.
        val backend = FakeProjectBackend()
        backend.seed(ServerProject(id = 9, name = "Base", updatedAt = "2026-09-01T00:00:00"))

        val deviceA = Fixture(backend = backend)
        val deviceB = Fixture(backend = backend)
        val engineA = deviceA.engine(backgroundScope)
        val engineB = deviceB.engine(backgroundScope)
        engineA.syncNow()
        engineB.syncNow()

        // Both edit locally, based on the same synced base (copy() keeps remoteUpdatedAt).
        deviceA.dao.upsert(
            deviceA.dao.findByServerId(9)!!.copy(
                name = "A edit", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
            ),
        )
        deviceB.dao.upsert(
            deviceB.dao.findByServerId(9)!!.copy(
                name = "B edit", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
            ),
        )

        backend.patchAppliedAt = "2026-09-10T12:00:00"
        engineA.syncNow()
        assertEquals("A edit", backend.projects.single { it.id == 9L }.name, "A's base is fresh → A's edit lands")

        // B's base is now stale (server moved) → the server (A's) version wins, B's edit is dropped.
        engineB.syncNow()
        assertEquals("A edit", backend.projects.single { it.id == 9L }.name)
        assertEquals("A edit", deviceB.dao.findByServerId(9)!!.name)

        // A converges on the same version on its next sync.
        engineA.syncNow()
        assertEquals("A edit", deviceA.dao.findByServerId(9)!!.name)
    }

    // ─── push failures ─────────────────────────────────────────────────────

    @Test
    fun offline_create_rejected_by_the_plan_limit_is_flagged_conflicted_not_dropped() = runTest {
        val f = Fixture()
        f.backend.planLimitReached = true
        f.dao.upsert(localProject("p1", name = "Second project", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByLocalId("p1")!!
        assertEquals(SyncStatus.CONFLICTED, row.syncStatus)
        assertEquals(SyncError.PLAN_LIMIT, row.lastSyncError)
        assertNull(row.serverId)
    }

    // ─── delete + server-side removal ──────────────────────────────────────

    @Test
    fun pending_delete_is_pushed_and_the_row_is_removed_locally() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 3, name = "To delete"))
        f.dao.upsert(
            localProject("p3", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.dao.findByLocalId("p3"))
        assertTrue(f.backend.projects.none { it.id == 3L })
    }

    @Test
    fun pull_drops_local_rows_deleted_on_the_server() = runTest {
        val f = Fixture()
        f.dao.upsert(
            localProject("gone", serverId = 42, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.dao.findByLocalId("gone"))
    }

    @Test
    fun pull_inserts_projects_first_seen_on_the_server() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 11, name = "Remote only", currency = "XAF", timezone = "Africa/Douala"))
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByServerId(11)!!
        assertEquals("Remote only", row.name)
        assertEquals("XAF", row.currency)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
    }

    // ─── single-project pull (detail + members) ─────────────────────────────

    @Test
    fun sync_project_pulls_the_project_and_its_members_into_the_local_store() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.backend.seedMembers(
            5,
            ServerMember(userId = 1, name = "Owner", email = "o@x.dev", role = "ADMIN"),
            ServerMember(userId = 2, name = "Sam", email = "s@x.dev", role = "SUPERVISOR"),
        )
        f.dao.upsert(
            localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val members = f.dao.observeMembers("p5").first()
        assertEquals(listOf(1L, 2L), members.map { it.userId }.sorted())
        assertEquals("SUPERVISOR", members.single { it.userId == 2L }.role)
    }

    @Test
    fun sync_project_replaces_members_that_are_no_longer_on_the_server() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.backend.seedMembers(5, ServerMember(userId = 1, name = "Owner", email = "o@x.dev"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.dao.upsertMembers(
            listOf(
                com.dmb.chantiertracker.data.local.db.ProjectMemberEntity("p5", 99, "Stale", "z@x.dev", "SUPERVISOR"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals(listOf(1L), f.dao.observeMembers("p5").first().map { it.userId })
    }

    @Test
    fun sync_project_pulls_pending_invitations_into_the_local_cache() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.backend.seedInvitation(com.dmb.chantiertracker.support.ServerInvitation(id = 1_200, projectId = 5, email = "sam@x.dev"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        assertEquals(listOf("sam@x.dev"), f.invitationDao.findForProject("p5").map { it.email })
    }

    @Test
    fun sync_project_stores_the_owner_plan_from_the_detail_pull() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal", ownerPlan = "SEMI_FLEX"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals("SEMI_FLEX", f.dao.findByLocalId("p5")?.ownerPlan)
    }

    // ADR-66 — the owner's combined entitlements travel with the same detail pull.
    @Test
    fun sync_project_stores_the_owner_entitlements_of_a_founder_on_the_free_plan() = runTest {
        val f = Fixture()
        f.backend.seed(
            ServerProject(
                id = 5, name = "Villa Vidal", ownerPlan = "FREE",
                ownerEntitlementsJson = """"ownerIsFounder":true,"ownerCanExportPdf":true,"ownerMaxHistoryDays":180,
                    |"ownerMaxVideos":5,"ownerMaxVideoDurationSeconds":120,"ownerMaxSupervisorsPerProject":3,""".trimMargin(),
            ),
        )
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        val stored = f.dao.findByLocalId("p5")
        assertEquals("FREE", stored?.ownerPlan)
        assertEquals(true, stored?.ownerIsFounder)
        assertEquals(true, stored?.ownerCanExportPdf)
        assertEquals(180, stored?.ownerMaxHistoryDays)
        assertEquals(5, stored?.ownerMaxVideos)
        assertEquals(120, stored?.ownerMaxVideoDurationSeconds)
        assertEquals(3, stored?.ownerMaxSupervisorsPerProject)
    }

    @Test
    fun sync_project_clears_the_invitation_cache_when_the_server_answers_403() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.invitationDao.upsertAll(listOf(com.dmb.chantiertracker.support.localInvitation(1, projectLocalId = "p5")))
        f.backend.invitationsForbidden = true
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals(emptyList(), f.invitationDao.findForProject("p5"), "a demoted user stops seeing stale invitations")
    }

    @Test
    fun sync_project_drops_a_local_row_the_server_no_longer_has() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertNull(f.dao.findByLocalId("p5"))
    }

    @Test
    fun sync_project_while_offline_is_skipped_and_never_touches_the_network() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncProject("p5"))
        assertTrue(f.backend.receivedMethods.isEmpty())
        assertEquals(SyncState.Offline, f.syncState.state.value)
    }

    @Test
    fun sync_project_still_without_a_server_id_after_the_push_is_skipped() = runTest {
        val f = Fixture()
        f.backend.planLimitReached = true
        f.dao.upsert(localProject("p5", serverId = null, pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncProject("p5"))
        assertEquals(SyncStatus.CONFLICTED, f.dao.findByLocalId("p5")!!.syncStatus)
        assertFalse(f.backend.receivedMethods.any { it.startsWith("GET /projects/") })
    }

    @Test
    fun sync_project_pushes_a_local_only_project_then_pulls_it_back() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", name = "Local only", serverId = null, pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val row = f.dao.findByLocalId("p5")!!
        assertNotNull(row.serverId)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
    }

    @Test
    fun sync_project_flushes_a_pending_local_edit_before_pulling() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Old name", updatedAt = "2026-09-01T10:00:00"))
        f.backend.patchAppliedAt = "2026-09-04T00:00:00"
        f.dao.upsert(
            localProject(
                "p5", name = "Fresh local edit", serverId = 5,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                remoteUpdatedAt = serverMillis("2026-09-01T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals("Fresh local edit", f.backend.projects.single { it.id == 5L }.name)
        assertEquals(SyncStatus.SYNCED, f.dao.findByLocalId("p5")!!.syncStatus)
    }

    // ─── background catch-up (ADR-22) ──────────────────────────────────────

    @Test
    fun a_pass_that_cannot_finish_hands_the_queue_to_the_os_scheduler() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsert(localProject("p1", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncNowOrDeferToOs())

        assertEquals(1, f.backgroundSync.expeditedCount, "offline write → hand the queue to the OS scheduler")
    }

    @Test
    fun a_pass_that_succeeds_schedules_no_background_work() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p1", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNowOrDeferToOs())

        assertEquals(SyncStatus.SYNCED, f.dao.findByLocalId("p1")!!.syncStatus)
        assertEquals(0, f.backgroundSync.expeditedCount)
    }

    @Test
    fun start_runs_a_catch_up_pass_every_interval_with_no_connectivity_change() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false) // keep the pass cheap: no real HTTP, just an offline no-op
        val engine = f.engine(backgroundScope, catchUpInterval = 5.minutes)

        engine.start()
        runCurrent()
        val afterStart = f.connectivity.isOnlineChecks

        advanceTimeBy(16.minutes) // three 5-minute ticks
        runCurrent()

        assertEquals(3, f.connectivity.isOnlineChecks - afterStart, "one catch-up pass per interval")
    }

    // ─── stages (children of a project) ────────────────────────────────────

    @Test
    fun a_stage_created_on_a_not_yet_synced_project_waits_then_pushes_once_the_project_has_a_server_id() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsert(localProject("proj-1", name = "Villa", pendingOp = PendingOp.CREATE))
        f.stageDao.upsert(localStage("st-1", projectLocalId = "proj-1", name = "Fondations", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncNow())
        assertTrue(f.backend.stages.isEmpty(), "nothing pushed while offline")

        f.connectivity.setOnline(true)
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val project = f.dao.findByLocalId("proj-1")!!
        assertNotNull(project.serverId)
        val stage = f.stageDao.findByLocalId("st-1")!!
        assertEquals(SyncStatus.SYNCED, stage.syncStatus)
        assertEquals(PendingOp.NONE, stage.pendingOp)
        assertEquals(project.serverId, f.backend.stages.single().projectId)
        assertEquals("Fondations", f.backend.stages.single().name)
    }

    @Test
    fun a_pending_stage_whose_parent_project_fails_to_push_stays_pending_not_errored() = runTest {
        val f = Fixture()
        f.backend.planLimitReached = true
        f.dao.upsert(localProject("proj-1", pendingOp = PendingOp.CREATE))
        f.stageDao.upsert(localStage("st-1", projectLocalId = "proj-1", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val stage = f.stageDao.findByLocalId("st-1")!!
        assertEquals(SyncStatus.PENDING, stage.syncStatus, "parent not on the server yet → stage still queued, no error")
        assertNull(stage.serverId)
        assertTrue(f.backend.stages.isEmpty())
    }

    @Test
    fun sync_project_pulls_the_project_stages_into_the_local_store() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre", estimatedBudget = 12000.0))
        f.backend.seedStage(ServerStage(id = 91, projectId = 5, name = "Toiture"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val stages = f.stageDao.findForProject("p5")
        assertEquals(listOf(90L, 91L), stages.mapNotNull { it.serverId }.sorted())
        assertEquals(12000.0, stages.single { it.serverId == 90L }.estimatedBudget)
    }

    @Test
    fun pull_stages_leaves_a_pending_local_edit_in_place() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Server name"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(
            localStage(
                "st-1", projectLocalId = "p5", name = "Local edit", serverId = 90,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals("Local edit", f.stageDao.findByServerId(90)!!.name, "pending local edit survives the pull")
        assertEquals("Local edit", f.backend.stages.single { it.id == 90L }.name, "then wins on push (last-writer)")
    }

    @Test
    fun pending_stage_delete_is_pushed_and_the_row_removed_locally() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "To remove"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(
            localStage("st-1", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.stageDao.findByLocalId("st-1"))
        assertTrue(f.backend.stages.none { it.id == 90L })
    }

    @Test
    fun sync_stage_pulls_one_stage_and_drops_it_on_404() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("gone", projectLocalId = "p5", serverId = 777, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncStage("gone"))
        assertNull(f.stageDao.findByLocalId("gone"))
    }

    @Test
    fun a_stage_edit_rejected_by_the_server_is_flagged_conflicted_not_dropped() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Original"))
        f.backend.stageWriteForbidden = true
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(
            localStage("st-1", projectLocalId = "p5", name = "Edited", serverId = 90, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val stage = f.stageDao.findByLocalId("st-1")!!
        assertEquals(SyncStatus.CONFLICTED, stage.syncStatus)
        assertEquals(SyncError.UPDATE_REFUSED, stage.lastSyncError)
    }

    // ─── Step 4: daily-log hierarchy sync (ADR-30) ─────────────────────────

    @Test
    fun the_whole_offline_hierarchy_is_pushed_in_dependency_order_on_reconnect() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsert(localProject("p1", pendingOp = PendingOp.CREATE))
        f.stageDao.upsert(localStage("s1", projectLocalId = "p1", pendingOp = PendingOp.CREATE))
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m1", projectLocalId = "p1", name = "Ciment"))
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l1", stageLocalId = "s1", date = "2026-09-05"))
        f.dailyEntryDao.upsert(com.dmb.chantiertracker.support.localDailyEntry("e1", dailyLogLocalId = "l1", type = "PURCHASE", summary = "12 sacs"))
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl1", entryLocalId = "e1", materialLocalId = "m1", quantity = 10.0, unitPrice = 3.0),
        )
        val path = f.fileStore.save(byteArrayOf(4, 2), "facture.jpg")
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("a1", entryLocalId = "e1", localPath = path))
        val engine = f.engine(backgroundScope)

        f.connectivity.setOnline(true)
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertNotNull(f.dao.findByLocalId("p1")!!.serverId)
        assertNotNull(f.stageDao.findByLocalId("s1")!!.serverId)
        assertNotNull(f.materialDao.findByLocalId("m1")!!.serverId)
        assertEquals(SyncStatus.SYNCED, f.dailyEntryDao.findByLocalId("e1")!!.syncStatus)
        assertNotNull(f.dailyEntryDao.findByLocalId("e1")!!.serverId)
        assertNotNull(f.dailyLogDao.findByLocalId("l1")!!.serverId, "the day learns its server id from the entry it created")
        assertEquals(SyncStatus.SYNCED, f.purchaseLineDao.findByLocalId("pl1")!!.syncStatus)
        assertNotNull(f.purchaseLineDao.findByLocalId("pl1")!!.serverId)
        assertEquals(SyncStatus.SYNCED, f.attachmentDao.findByLocalId("a1")!!.syncStatus)

        assertEquals(1, f.backend.entries.size)
        assertEquals(1, f.backend.purchaseLines.size)
        assertEquals(1, f.backend.attachments.size)
        assertEquals(3.0, f.backend.purchaseLines.single().unitPrice)
    }

    @Test
    fun a_line_whose_parent_entry_is_not_on_the_server_yet_stays_pending_and_is_not_an_error() = runTest {
        val f = Fixture()
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("m1", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl1", entryLocalId = "ghost-entry", materialLocalId = "m1"),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertEquals(SyncStatus.PENDING, f.purchaseLineDao.findByLocalId("pl1")!!.syncStatus, "still waiting for its parent, not rejected")
    }

    @Test
    fun an_insufficient_stock_rejection_marks_the_line_conflicted_and_does_not_retry() = runTest {
        val f = Fixture()
        f.backend.lineWriteConflict = true
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("m1", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e1", serverId = 40, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl1", entryLocalId = "e1", materialLocalId = "m1", quantity = 99.0),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.consumptionLineDao.findByLocalId("cl1")!!
        assertEquals(SyncStatus.CONFLICTED, row.syncStatus)
        assertEquals(SyncError.REJECTED, row.lastSyncError)
    }

    @Test
    fun sync_project_pulls_materials_and_day_summaries_but_not_the_entries() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        val log = f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 900, dailyLogId = log.id, type = "PURCHASE", summary = "x"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("st90", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals(listOf("Ciment"), f.materialDao.stored.map { it.name })
        val days = f.dailyLogDao.findForStage("st90")
        assertEquals(listOf("2026-09-05"), days.map { it.date })
        assertEquals(800L, days.single().serverId)
        assertTrue(f.dailyEntryDao.stored.isEmpty(), "entries are left to syncLog")
    }

    @Test
    fun sync_log_pulls_entries_lines_and_downloads_photos_for_a_synced_day() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        val log = f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        val purchase = f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE", summary = "12 sacs"))
        f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 1000, entryId = purchase.id, materialId = 7, quantity = 12.0, unitPrice = 3.5))
        f.backend.seedAttachment(com.dmb.chantiertracker.support.ServerAttachment(id = 1100, entryId = purchase.id))
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("m7", projectLocalId = "p5", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l800", stageLocalId = "st90", date = "2026-09-05", serverId = 800))
        val engine = f.engine(backgroundScope)

        engine.syncLog("l800")

        val entries = f.dailyEntryDao.findForLog("l800")
        assertEquals(listOf("PURCHASE"), entries.map { it.type })
        assertEquals("12 sacs", entries.single().summary)
        val lines = f.purchaseLineDao.findForEntry(entries.single().localId)
        assertEquals(listOf(12.0), lines.map { it.quantity })
        assertEquals("m7", lines.single().materialLocalId, "the line's material id is resolved from server id")
        val attachments = f.attachmentDao.findForEntry(entries.single().localId)
        assertEquals(1, attachments.size)
        assertTrue(attachments.single().localPath in f.fileStore.storedPaths, "the photo was downloaded to a local file")
    }

    @Test
    fun a_video_attachment_added_elsewhere_is_downloaded_on_pull() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "S"))
        val log = f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        val purchase = f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE"))
        f.backend.seedAttachment(
            com.dmb.chantiertracker.support.ServerAttachment(
                id = 1100, entryId = purchase.id, mimeType = "video/mp4", originalName = "clip.mp4", durationSeconds = 42,
            ),
        )
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l800", stageLocalId = "st90", date = "2026-09-05", serverId = 800))
        val engine = f.engine(backgroundScope)

        engine.syncLog("l800")

        val stored = f.attachmentDao.findForEntry(f.dailyEntryDao.findForLog("l800").single().localId)
        assertEquals(1, stored.size)
        val video = stored.single()
        assertEquals("video/mp4", video.mimeType)
        assertEquals(42, video.durationSeconds)
        assertEquals(SyncStatus.SYNCED, video.syncStatus)
        assertEquals(PendingOp.NONE, video.pendingOp)
        assertTrue(video.localPath in f.fileStore.storedPaths, "the transcoded video was downloaded to a local file")
        assertTrue(video.localPath.endsWith(".mp4"), "the local copy keeps a video extension")
    }

    @Test
    fun pushing_an_entry_update_the_server_already_removed_keeps_the_edit_as_deleted_on_server() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "S"))
        f.stageDao.upsert(localStage("st90", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l1", stageLocalId = "st90", date = "2026-09-05", serverId = 800))
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e1", dailyLogLocalId = "l1", serverId = 12345, type = "PURCHASE", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dailyEntryDao.findByLocalId("e1")!!
        assertEquals(SyncStatus.CONFLICTED, row.syncStatus, "the unsent edit is never dropped (ADR-70)")
        assertEquals(SyncError.DELETED_ON_SERVER, row.lastSyncError)
    }

    @Test
    fun deleting_a_synced_consumption_line_offline_pushes_the_delete_on_reconnect() = runTest {
        val f = Fixture()
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl1", serverId = 555, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 555, entryId = 40, materialId = 7, quantity = 4.0))
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.consumptionLineDao.findByLocalId("cl1"))
        assertTrue(f.backend.consumptionLines.isEmpty())
        assertTrue(f.backend.receivedMethods.any { it == "DELETE /consumption-lines/555" })
    }

    @Test
    fun a_consumption_line_delete_refused_by_the_server_restores_the_line_instead_of_retrying_forever() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl1", serverId = 555, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 555, entryId = 40, materialId = 7, quantity = 4.0))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val row = f.consumptionLineDao.findByLocalId("cl1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertEquals(SyncError.REJECTED, row.lastSyncError)
        assertEquals(1, f.backend.consumptionLines.size, "the server kept the line")
    }

    @Test
    fun a_purchase_line_delete_refused_by_the_server_restores_the_line() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl1", serverId = 300, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val row = f.purchaseLineDao.findByLocalId("pl1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertEquals(SyncError.REJECTED, row.lastSyncError)
    }

    @Test
    fun an_attachment_delete_refused_by_the_server_brings_the_file_back() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.seedAttachment(com.dmb.chantiertracker.support.ServerAttachment(id = 1100, entryId = 40, bytes = byteArrayOf(7, 7)))
        f.attachmentDao.upsert(
            com.dmb.chantiertracker.support.localAttachment(
                "a1", serverId = 1100, localPath = "freed/a1.jpg", pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING,
            ),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val row = f.attachmentDao.findByLocalId("a1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertEquals(SyncError.REJECTED, row.lastSyncError)
        assertTrue(row.localPath in f.fileStore.storedPaths, "the bytes were re-downloaded — the local file had been freed at delete time")
        assertEquals(1, f.backend.attachments.size)
    }

    @Test
    fun a_refused_delete_no_longer_blocks_the_pushes_queued_behind_it() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl1", serverId = 555, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.attachmentDao.upsert(
            com.dmb.chantiertracker.support.localAttachment("a1", serverId = 1100, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.backend.seedAttachment(com.dmb.chantiertracker.support.ServerAttachment(id = 1100, entryId = 40))
        f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 555, entryId = 40, materialId = 7, quantity = 4.0))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow(), "before the fix the 403 aborted the whole pass")

        assertEquals(PendingOp.NONE, f.consumptionLineDao.findByLocalId("cl1")!!.pendingOp)
        assertEquals(PendingOp.NONE, f.attachmentDao.findByLocalId("a1")!!.pendingOp)
    }

    @Test
    fun a_transient_server_error_on_a_delete_is_still_retried_not_treated_as_a_refusal() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.InternalServerError
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl1", serverId = 555, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 555, entryId = 40, materialId = 7, quantity = 4.0))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Failed>(engine.syncNow())
        val stillPending = f.consumptionLineDao.findByLocalId("cl1")!!
        assertEquals(PendingOp.DELETE, stillPending.pendingOp, "kept for the next pass")

        f.backend.deleteStatus = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertNull(f.consumptionLineDao.findByLocalId("cl1"))
        assertTrue(f.backend.consumptionLines.isEmpty())
    }

    // ─── ADR-63 : projet / étape / entrée ────────────────────────────────────

    @Test
    fun a_project_delete_refused_by_the_server_restores_the_project_and_leaves_its_stages_untouched() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.seed(ServerProject(id = 3, name = "Nom serveur"))
        f.dao.upsert(
            localProject("p3", name = "Nom local", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.stageDao.upsert(localStage("st1", projectLocalId = "p3", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val row = f.dao.findByLocalId("p3")!!
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals("Nom serveur", row.name, "the pull that follows in the same pass reconciles the restored row with the server")
        assertNotNull(f.stageDao.findByLocalId("st1"), "the children were never tombstoned, so nothing cascades")
        assertEquals(1, f.backend.projects.size, "the server kept the project")
    }

    @Test
    fun a_refused_project_delete_by_a_user_who_lost_access_ends_with_the_project_dropped_by_the_pull() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.dao.upsert(
            localProject("p3", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertNull(f.dao.findByLocalId("p3"), "no longer listed by the server: the restored row is dropped, not left as a ghost")
    }

    @Test
    fun a_project_already_gone_on_the_server_is_removed_locally_not_restored() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.NotFound
        f.backend.seed(ServerProject(id = 3, name = "Villa"))
        f.dao.upsert(
            localProject("p3", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()
        f.backend.deleteStatus = null

        assertNull(f.dao.findByLocalId("p3"))
    }

    @Test
    fun a_transient_server_error_on_a_project_delete_keeps_it_pending_for_the_next_pass() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.InternalServerError
        f.backend.seed(ServerProject(id = 3, name = "Villa"))
        f.dao.upsert(
            localProject("p3", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Failed>(engine.syncNow())
        assertEquals(PendingOp.DELETE, f.dao.findByLocalId("p3")!!.pendingOp)

        f.backend.deleteStatus = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertNull(f.dao.findByLocalId("p3"))
        assertTrue(f.backend.projects.isEmpty())
    }

    @Test
    fun a_stage_delete_refused_by_the_server_restores_the_stage_and_keeps_its_day_logs() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(
            localStage("st1", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l1", stageLocalId = "st1"))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val row = f.stageDao.findByLocalId("st1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertEquals(SyncError.REJECTED, row.lastSyncError)
        assertNotNull(f.dailyLogDao.findByLocalId("l1"), "the stage's day logs were never tombstoned")
        assertEquals(1, f.backend.stages.size)
    }

    @Test
    fun a_stage_already_gone_on_the_server_is_removed_locally_not_restored() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.NotFound
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(
            localStage("st1", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.stageDao.findByLocalId("st1"))
    }

    @Test
    fun a_transient_server_error_on_a_stage_delete_keeps_it_pending_for_the_next_pass() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.InternalServerError
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(
            localStage("st1", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Failed>(engine.syncNow())
        assertEquals(PendingOp.DELETE, f.stageDao.findByLocalId("st1")!!.pendingOp)

        f.backend.deleteStatus = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertNull(f.stageDao.findByLocalId("st1"))
        assertTrue(f.backend.stages.isEmpty())
    }

    @Test
    fun an_entry_delete_refused_by_the_server_restores_the_entry_and_keeps_its_lines() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 40, dailyLogId = 800, type = "PURCHASE"))
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e1", serverId = 40, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl1", entryLocalId = "e1", serverId = 555, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val row = f.dailyEntryDao.findByLocalId("e1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertEquals(SyncError.REJECTED, row.lastSyncError)
        assertNotNull(f.consumptionLineDao.findByLocalId("cl1"), "the entry's lines were never tombstoned")
        assertEquals(1, f.backend.entries.size)
    }

    @Test
    fun an_entry_already_gone_on_the_server_is_removed_locally_not_restored() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.NotFound
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e1", serverId = 40, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.dailyEntryDao.findByLocalId("e1"))
    }

    @Test
    fun a_transient_server_error_on_an_entry_delete_keeps_it_pending_for_the_next_pass() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.InternalServerError
        f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 40, dailyLogId = 800, type = "PURCHASE"))
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e1", serverId = 40, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Failed>(engine.syncNow())
        assertEquals(PendingOp.DELETE, f.dailyEntryDao.findByLocalId("e1")!!.pendingOp)

        f.backend.deleteStatus = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertNull(f.dailyEntryDao.findByLocalId("e1"))
    }

    @Test
    fun a_refused_project_delete_no_longer_blocks_the_stage_and_entry_deletes_queued_behind_it() = runTest {
        val f = Fixture()
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.seed(ServerProject(id = 3, name = "Villa"))
        f.dao.upsert(localProject("p3", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.backend.seed(ServerProject(id = 5, name = "Autre"))
        f.stageDao.upsert(
            localStage("st1", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e1", serverId = 40, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow(), "before the fix the first 403 aborted the whole pass")

        assertEquals(PendingOp.NONE, f.dao.findByLocalId("p3")!!.pendingOp)
        assertEquals(PendingOp.NONE, f.stageDao.findByLocalId("st1")!!.pendingOp)
        assertEquals(PendingOp.NONE, f.dailyEntryDao.findByLocalId("e1")!!.pendingOp)
    }

    // ─── ADR-70 — a row, a parent or the access gone on the server ─────────

    private suspend fun Fixture.siteWithAPendingEntry(project: String, projectServerId: Long, stageServerId: Long, entry: String) {
        dao.upsert(localProject(project, serverId = projectServerId, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        stageDao.upsert(localStage("st-$project", projectLocalId = project, serverId = stageServerId, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("log-$project", stageLocalId = "st-$project"))
        dailyEntryDao.upsert(com.dmb.chantiertracker.support.localDailyEntry(entry, dailyLogLocalId = "log-$project", type = "WORK", summary = "Coulage"))
    }

    private fun assertDeletedOnServer(status: SyncStatus?, error: String?, what: String) {
        assertEquals(SyncStatus.CONFLICTED, status, "$what is kept, refused")
        assertEquals(SyncError.DELETED_ON_SERVER, error, "$what carries the dedicated reason")
    }

    @Test
    fun an_entry_on_a_stage_deleted_on_the_server_is_kept_and_the_pass_goes_on_to_the_other_projects() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seed(ServerProject(id = 6, name = "Atelier"))
        f.backend.seedStage(ServerStage(id = 60, projectId = 6, name = "Charpente"))
        f.siteWithAPendingEntry("p5", projectServerId = 5, stageServerId = 50, entry = "e5")
        f.siteWithAPendingEntry("p6", projectServerId = 6, stageServerId = 60, entry = "e6")
        f.backend.goneOnServer += Regex("^/stages/50(/|$)")
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow(), "a 404 no longer aborts the pass")
        val e5 = f.dailyEntryDao.findByLocalId("e5")
        assertDeletedOnServer(e5?.syncStatus, e5?.lastSyncError, "the entry on the deleted stage")
        assertEquals("Coulage", e5?.summary)
        assertEquals(SyncStatus.SYNCED, f.dailyEntryDao.findByLocalId("e6")?.syncStatus, "the other project's entry went out in the same pass")

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        val stage = f.stageDao.findByLocalId("st-p5")
        assertDeletedOnServer(stage?.syncStatus, stage?.lastSyncError, "the stage missing from the server, holding an unsent entry,")
        assertNotNull(f.dailyEntryDao.findByLocalId("e5"), "no cascade: the orphan survives the pull")

        f.backend.receivedMethods.clear()
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertTrue(f.backend.receivedMethods.none { it.contains("/stages/50") }, "a row deleted on the server is not resent: ${f.backend.receivedMethods}")
    }

    @Test
    fun a_project_deleted_on_the_server_stays_as_a_ghost_while_it_holds_unsent_entries() = runTest {
        val f = Fixture()
        f.siteWithAPendingEntry("p5", projectServerId = 5, stageServerId = 50, entry = "e5")
        f.backend.goneOnServer += Regex("^/(projects|stages)/(5|50)(/|$)")
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val project = f.dao.findByLocalId("p5")
        assertDeletedOnServer(project?.syncStatus, project?.lastSyncError, "the project no longer listed")
        assertEquals(5L, project?.serverId, "its server id is kept, so a re-invitation finds it again")
        val stage = f.stageDao.findByLocalId("st-p5")
        assertDeletedOnServer(stage?.syncStatus, stage?.lastSyncError, "its stage")
        val entry = f.dailyEntryDao.findByLocalId("e5")
        assertDeletedOnServer(entry?.syncStatus, entry?.lastSyncError, "the orphan entry")

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"), "opening the ghost project is not an error")
        assertNotNull(f.dailyEntryDao.findByLocalId("e5"))
    }

    @Test
    fun a_project_deleted_on_the_server_with_nothing_unsent_below_is_dropped_as_before() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("st5", projectLocalId = "p5", serverId = 50, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l5", stageLocalId = "st5", serverId = 800))
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e5", dailyLogLocalId = "l5", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertNull(f.dao.findByLocalId("p5"))
    }

    @Test
    fun a_reinvited_supervisor_gets_the_ghost_project_back_synced_and_the_orphan_entry_stays_deleted_on_server() = runTest {
        val f = Fixture()
        f.siteWithAPendingEntry("p5", projectServerId = 5, stageServerId = 50, entry = "e5")
        f.backend.goneOnServer += Regex("^/(projects|stages)/(5|50)(/|$)")
        val engine = f.engine(backgroundScope)
        engine.syncNow()
        assertEquals(SyncError.DELETED_ON_SERVER, f.dao.findByLocalId("p5")?.lastSyncError)

        f.backend.goneOnServer.clear()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 50, projectId = 5, name = "Gros œuvre"))
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val project = f.dao.findByLocalId("p5")!!
        assertEquals(SyncStatus.SYNCED, project.syncStatus, "the ghost is the project again")
        assertNull(project.lastSyncError)
        assertEquals(listOf("p5"), f.dao.findAll().map { it.localId }, "matched by server id, not duplicated")
        val stage = f.stageDao.findByLocalId("st-p5")!!
        assertEquals(SyncStatus.SYNCED, stage.syncStatus)
        assertNull(stage.lastSyncError)
        val entry = f.dailyEntryDao.findByLocalId("e5")
        assertDeletedOnServer(entry?.syncStatus, entry?.lastSyncError, "the orphan entry, left for an explicit retry (A-2),")
        assertTrue(f.backend.entries.isEmpty(), "never resent on its own")
    }

    @Test
    fun an_edit_to_an_entry_deleted_on_the_server_keeps_the_edit_its_pending_line_and_its_photo() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "S"))
        f.stageDao.upsert(localStage("st90", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l1", stageLocalId = "st90", serverId = 800))
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry(
                "e1", dailyLogLocalId = "l1", serverId = 12345, summary = "Livraison partielle", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
            ),
        )
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m1", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl1", entryLocalId = "e1", materialLocalId = "m1"))
        val path = f.fileStore.save(byteArrayOf(4, 2), "bon.jpg")
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("a1", entryLocalId = "e1", localPath = path))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val entry = f.dailyEntryDao.findByLocalId("e1")
        assertDeletedOnServer(entry?.syncStatus, entry?.lastSyncError, "the edited entry")
        assertEquals("Livraison partielle", entry?.summary)
        val line = f.purchaseLineDao.findByLocalId("pl1")
        assertDeletedOnServer(line?.syncStatus, line?.lastSyncError, "its pending line")
        val photo = f.attachmentDao.findByLocalId("a1")
        assertDeletedOnServer(photo?.syncStatus, photo?.lastSyncError, "its pending photo")
        assertTrue(path in f.fileStore.storedPaths, "the photo file is kept")
        assertTrue(f.backend.purchaseLines.isEmpty() && f.backend.attachments.isEmpty(), "nothing sent to an entry that no longer exists")
    }

    @Test
    fun a_line_on_an_entry_deleted_on_the_server_is_kept_and_the_entry_too_when_its_day_is_pulled() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "S"))
        f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        f.backend.goneOnServer += Regex("^/entries/900(/|$)")
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        f.dailyEntryDao.upsert(
            com.dmb.chantiertracker.support.localDailyEntry("e900", dailyLogLocalId = "l800", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m1", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("cl1", entryLocalId = "e900", materialLocalId = "m1"))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        val line = f.consumptionLineDao.findByLocalId("cl1")
        assertDeletedOnServer(line?.syncStatus, line?.lastSyncError, "the line sent to a deleted entry")
        val entry = f.dailyEntryDao.findByLocalId("e900")
        assertDeletedOnServer(entry?.syncStatus, entry?.lastSyncError, "the entry missing from its day, holding an unsent line,")
        f.backend.receivedMethods.clear()
        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertTrue(f.backend.receivedMethods.none { it.contains("/entries/900") }, "the ghost entry's lines are not pulled: ${f.backend.receivedMethods}")
    }

    @Test
    fun a_stage_created_on_a_project_deleted_on_the_server_is_kept_deleted_on_server() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("st-new", projectLocalId = "p5", name = "Toiture"))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val stage = f.stageDao.findByLocalId("st-new")
        assertDeletedOnServer(stage?.syncStatus, stage?.lastSyncError, "the new stage")
        assertDeletedOnServer(f.dao.findByLocalId("p5")?.syncStatus, f.dao.findByLocalId("p5")?.lastSyncError, "its project")
    }

    @Test
    fun a_material_created_on_a_project_deleted_on_the_server_is_kept_deleted_on_server() = runTest {
        val f = Fixture()
        f.backend.goneOnServer += Regex("^/projects/5/materials$")
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m-new", projectLocalId = "p5", name = "Sable"))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val material = f.materialDao.findByLocalId("m-new")
        assertDeletedOnServer(material?.syncStatus, material?.lastSyncError, "the new material")
    }

    // ─── ADR-71 — the stock comes from the server ──────────────────────────

    private fun stockFixture(): Fixture {
        val entryToProject = { mapOf("e900" to "p5", "e901" to "p5") }
        return Fixture(
            purchaseLineDao = com.dmb.chantiertracker.support.FakePurchaseLineDao(projectForEntry = entryToProject),
            consumptionLineDao = com.dmb.chantiertracker.support.FakeConsumptionLineDao(projectForEntry = entryToProject),
        )
    }

    private suspend fun Fixture.siteWithCement(serverPurchase: Double? = 12.0) {
        backend.seed(ServerProject(id = 5, name = "Villa"))
        backend.seedStage(ServerStage(id = 90, projectId = 5, name = "Gros œuvre"))
        backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE"))
        backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 901, dailyLogId = 800, type = "WORK"))
        backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 7, projectId = 5, name = "Ciment", unit = "sac"))
        serverPurchase?.let {
            backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 5000, entryId = 900, materialId = 7, quantity = it, unitPrice = 6.0))
        }
        dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        stageDao.upsert(localStage("st90", projectLocalId = "p5", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l800", stageLocalId = "st90", serverId = 800))
        dailyEntryDao.upsert(com.dmb.chantiertracker.support.localDailyEntry("e900", dailyLogLocalId = "l800", serverId = 900, type = "PURCHASE", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        dailyEntryDao.upsert(com.dmb.chantiertracker.support.localDailyEntry("e901", dailyLogLocalId = "l800", serverId = 901, type = "WORK", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m7", projectLocalId = "p5", name = "Ciment", unit = "sac", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    private suspend fun Fixture.cementAvailable(): Double =
        stock().materials.single { it.materialLocalId == "m7" }.available

    private suspend fun Fixture.stock() =
        com.dmb.chantiertracker.data.repository.MaterialRepositoryImpl(
            materialDao, purchaseLineDao, consumptionLineDao, stockDao, com.dmb.chantiertracker.support.FakeSyncer(), AppCoroutineScope(),
        ).observeStock("p5").first()

    private fun Fixture.stockRequests() = backend.receivedMethods.count { it == "GET /projects/5/stock" }

    @Test
    fun opening_the_project_loads_the_server_stock_even_for_days_never_opened() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        val engine = f.engine(backgroundScope)
        assertFalse(f.stock().isLoaded)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        assertTrue(f.stock().isLoaded)
        assertEquals(12.0, f.cementAvailable(), "the purchase of a day this device never opened is counted (C-1)")
        assertTrue(f.purchaseLineDao.stored.isEmpty(), "without downloading that day's lines")
    }

    @Test
    fun opening_a_day_reloads_the_stock_and_its_pulled_lines_are_not_counted_twice() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 5001, entryId = 900, materialId = 7, quantity = 3.0, unitPrice = 6.0))

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertEquals(2, f.purchaseLineDao.stored.size, "both lines pulled")
        assertEquals(15.0, f.cementAvailable(), "the reloaded counter, not counter + pulled lines")
    }

    @Test
    fun a_pending_purchase_counts_at_once_and_the_figure_does_not_move_when_it_is_sent() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-new", entryLocalId = "e900", materialLocalId = "m7", quantity = 5.0))
        assertEquals(17.0, f.cementAvailable())
        val requestsBefore = f.stockRequests()

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(17.0, f.cementAvailable(), "no double count once sent: the counter took the very +5 the server applied")
        assertEquals(SyncStatus.SYNCED, f.purchaseLineDao.findByLocalId("pl-new")?.syncStatus)
        assertEquals(requestsBefore, f.stockRequests(), "a creation needs no reload: its delta is exact")
        assertEquals(17.0, f.backend.purchaseLines.sumOf { it.quantity })
    }

    @Test
    fun a_pass_that_sends_lines_of_a_project_whose_stock_was_never_loaded_loads_it() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        val engine = f.engine(backgroundScope)
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-new", entryLocalId = "e900", materialLocalId = "m7", quantity = 5.0))
        assertFalse(f.stock().isLoaded)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertTrue(f.stock().isLoaded, "the device that just wrote to the project now knows its stock")
        assertEquals(17.0, f.cementAvailable())
        assertTrue(f.stockDao.findProjectsNeedingRefresh().isEmpty())
    }

    @Test
    fun a_pending_edit_counts_only_its_difference_and_the_stock_follows_the_server_once_sent() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 10.0)
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl", entryLocalId = "e900", materialLocalId = "m7", quantity = 4.0, serverId = 5000, pendingOp = PendingOp.UPDATE)
                .copy(serverQuantity = 10.0),
        )
        assertEquals(4.0, f.cementAvailable())

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(4.0, f.cementAvailable())
        assertEquals(4.0, f.purchaseLineDao.findByLocalId("pl")?.serverQuantity)
        assertEquals(4.0, f.backend.purchaseLines.single().quantity)
    }

    @Test
    fun a_pending_delete_removes_what_the_server_held_and_the_stock_follows_once_sent() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 10.0)
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl", entryLocalId = "e900", materialLocalId = "m7", quantity = 10.0, serverId = 5000, pendingOp = PendingOp.DELETE)
                .copy(serverQuantity = 10.0),
        )
        assertEquals(0.0, f.cementAvailable())

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(0.0, f.cementAvailable())
        assertNull(f.purchaseLineDao.findByLocalId("pl"))
    }

    @Test
    fun a_consumption_the_server_refuses_leaves_the_stock_at_what_the_server_holds() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        f.backend.lineWriteConflict = true
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("cl", entryLocalId = "e901", materialLocalId = "m7", quantity = 99.0))
        assertEquals(-87.0, f.cementAvailable(), "pending: counted until the server answers")

        engine.syncNow()

        assertEquals(SyncStatus.CONFLICTED, f.consumptionLineDao.findByLocalId("cl")?.syncStatus)
        assertEquals(12.0, f.cementAvailable(), "refused: the server does not hold it, neither does the displayed stock")
    }

    @Test
    fun a_deleted_stage_reloads_the_stock_at_the_end_of_the_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.backend.seedStage(ServerStage(id = 91, projectId = 5, name = "Toiture"))
        f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 801, stageId = 91, date = "2026-09-06"))
        f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 902, dailyLogId = 801, type = "PURCHASE"))
        f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 5002, entryId = 902, materialId = 7, quantity = 4.0, unitPrice = 6.0))
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        assertEquals(16.0, f.cementAvailable())
        f.stageDao.upsert(localStage("st91", projectLocalId = "p5", serverId = 91, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        val requestsBefore = f.stockRequests()

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(12.0, f.cementAvailable(), "the server released lines this device never downloaded")
        assertEquals(requestsBefore + 1, f.stockRequests())
        assertTrue(f.stockDao.findProjectsNeedingRefresh().isEmpty())
    }

    @Test
    fun a_failed_reload_is_retried_by_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.backend.seedStage(ServerStage(id = 91, projectId = 5, name = "Toiture"))
        f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 801, stageId = 91, date = "2026-09-06"))
        f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 902, dailyLogId = 801, type = "PURCHASE"))
        f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 5002, entryId = 902, materialId = 7, quantity = 4.0, unitPrice = 6.0))
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        f.stageDao.upsert(localStage("st91", projectLocalId = "p5", serverId = 91, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        f.backend.stockStatus = io.ktor.http.HttpStatusCode.InternalServerError

        assertIs<SyncOutcome.Failed>(engine.syncNow())
        assertEquals(listOf("p5"), f.stockDao.findProjectsNeedingRefresh(), "the reload is owed")

        f.backend.stockStatus = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertEquals(12.0, f.cementAvailable())
        assertTrue(f.stockDao.findProjectsNeedingRefresh().isEmpty())
    }

    @Test
    fun the_server_stock_is_read_page_by_page() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        (1..230).forEach { n ->
            f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 10_000L + n, projectId = 5, name = "Matériau $n", unit = "u"))
            f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 20_000L + n, entryId = 900, materialId = 10_000L + n, quantity = 1.0, unitPrice = 1.0))
        }
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals(230, f.stockDao.counters.value.size)
        assertEquals(3, f.stockRequests(), "100 + 100 + 30")
    }

    @Test
    fun every_path_that_makes_a_line_synced_records_what_the_server_holds() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 10.0)
        f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 6000, entryId = 901, materialId = 7, quantity = 3.0))
        val engine = f.engine(backgroundScope)
        engine.syncProject("p5")
        engine.syncLog("l800")
        val pulledPurchase = f.purchaseLineDao.stored.single { it.serverId == 5000L }
        val pulledConsumption = f.consumptionLineDao.stored.single { it.serverId == 6000L }
        assertEquals(10.0, pulledPurchase.serverQuantity, "pull of a new purchase line")
        assertEquals(3.0, pulledConsumption.serverQuantity, "pull of a new consumption line")

        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-new", entryLocalId = "e900", materialLocalId = "m7", quantity = 5.0))
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("cl-new", entryLocalId = "e901", materialLocalId = "m7", quantity = 2.0))
        engine.syncNow()
        assertEquals(5.0, f.purchaseLineDao.findByLocalId("pl-new")?.serverQuantity, "push of a purchase creation")
        assertEquals(2.0, f.consumptionLineDao.findByLocalId("cl-new")?.serverQuantity, "push of a consumption creation")

        f.purchaseLineDao.upsert(pulledPurchase.copy(quantity = 7.0, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.consumptionLineDao.upsert(pulledConsumption.copy(quantity = 1.0, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        engine.syncNow()
        assertEquals(7.0, f.purchaseLineDao.findByLocalId(pulledPurchase.localId)?.serverQuantity, "push of a purchase edit")
        assertEquals(1.0, f.consumptionLineDao.findByLocalId(pulledConsumption.localId)?.serverQuantity, "push of a consumption edit")

        f.backend.purchaseLines.single { it.id == 5000L }.quantity = 8.0
        f.backend.consumptionLines.single { it.id == 6000L }.quantity = 2.5
        engine.syncLog("l800")
        assertEquals(8.0, f.purchaseLineDao.findByLocalId(pulledPurchase.localId)?.serverQuantity, "pull of a purchase line changed elsewhere")
        assertEquals(2.5, f.consumptionLineDao.findByLocalId(pulledConsumption.localId)?.serverQuantity, "pull of a consumption line changed elsewhere")

        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Forbidden
        val created = f.purchaseLineDao.findByLocalId("pl-new")!!
        val createdConsumption = f.consumptionLineDao.findByLocalId("cl-new")!!
        f.purchaseLineDao.upsert(created.copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        f.consumptionLineDao.upsert(createdConsumption.copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        engine.syncNow()
        f.backend.deleteStatus = null
        assertEquals(SyncStatus.SYNCED, f.purchaseLineDao.findByLocalId("pl-new")?.syncStatus)
        assertEquals(5.0, f.purchaseLineDao.findByLocalId("pl-new")?.serverQuantity, "restore of a purchase line after a refused delete")
        assertEquals(2.0, f.consumptionLineDao.findByLocalId("cl-new")?.serverQuantity, "restore of a consumption line after a refused delete")

        f.purchaseLineDao.stored.filter { it.syncStatus == SyncStatus.SYNCED }.forEach { line ->
            assertEquals(f.backend.purchaseLines.single { it.id == line.serverId }.quantity, line.serverQuantity, "purchase ${line.localId}")
        }
        f.consumptionLineDao.stored.filter { it.syncStatus == SyncStatus.SYNCED }.forEach { line ->
            assertEquals(f.backend.consumptionLines.single { it.id == line.serverId }.quantity, line.serverQuantity, "consumption ${line.localId}")
        }
        assertEquals(f.backend.purchaseLines.sumOf { it.quantity } - f.backend.consumptionLines.sumOf { it.quantity }, f.cementAvailable(), "and the displayed stock is the server's")
    }

    // ─── C-3 — the same material created on two devices ────────────────────

    @Test
    fun a_material_the_server_already_has_under_the_same_name_is_adopted_and_its_lines_go_out_in_the_same_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 9, projectId = 5, name = "Sable", unit = "t"))
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m-sand", projectLocalId = "p5", name = "Sable", unit = "tonne"))
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-sand", entryLocalId = "e900", materialLocalId = "m-sand", quantity = 3.0))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val sand = f.materialDao.findByLocalId("m-sand")!!
        assertEquals(9L, sand.serverId, "DUPLICATE_MATERIAL: the server's « Sable » is adopted")
        assertEquals(SyncStatus.SYNCED, sand.syncStatus)
        assertEquals("t", sand.unit, "the server's unit wins")
        assertEquals(SyncStatus.SYNCED, f.purchaseLineDao.findByLocalId("pl-sand")?.syncStatus, "the purchase no longer waits forever")
        assertEquals(9L, f.backend.purchaseLines.single().materialId)
        assertEquals(1, f.backend.materials.count { it.name == "Sable" })
    }

    @Test
    fun a_material_refused_before_this_fix_is_adopted_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 9, projectId = 5, name = "Sable", unit = "t"))
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("m-sand", projectLocalId = "p5", name = "Sable", unit = "t")
                .copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertEquals(9L, f.materialDao.findByLocalId("m-sand")?.serverId)
    }

    @Test
    fun pulling_a_server_material_named_like_a_local_only_one_adopts_it_instead_of_failing_silently() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 9, projectId = 5, name = "Sable", unit = "t"))
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("m-sand", projectLocalId = "p5", name = "Sable", unit = "t")
                .copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED),
        )
        f.connectivity.setOnline(true)
        val engine = f.engine(backgroundScope)
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("probe", projectLocalId = "p5", name = "Sable", unit = "t"))
        assertNull(f.materialDao.findByLocalId("probe"), "the trap itself: a new row clashing on the name is silently not stored")

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val sands = f.materialDao.stored.filter { it.name == "Sable" }
        assertEquals(listOf("m-sand"), sands.map { it.localId }, "one « Sable », the local row")
        assertEquals(9L, sands.single().serverId, "it now carries the server's id, so its lines can go out")
    }

    @Test
    fun a_server_material_renamed_onto_a_local_only_one_merges_them_and_moves_the_lines() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 9, projectId = 5, name = "Sable", unit = "t"))
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m-old-name", projectLocalId = "p5", name = "Gravier", unit = "t", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("m-local", projectLocalId = "p5", name = "Sable", unit = "t")
                .copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED),
        )
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-local", entryLocalId = "e900", materialLocalId = "m-local", quantity = 2.0))
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")
        engine.syncNow()

        assertNull(f.materialDao.findByLocalId("m-local"), "the local duplicate is merged away")
        assertEquals("Sable", f.materialDao.findByLocalId("m-old-name")?.name)
        assertEquals("m-old-name", f.purchaseLineDao.findByLocalId("pl-local")?.materialLocalId, "its line now points at the kept material")
        assertEquals(SyncStatus.SYNCED, f.purchaseLineDao.findByLocalId("pl-local")?.syncStatus)
        assertEquals(9L, f.backend.purchaseLines.single().materialId)
    }

    @Test
    fun start_is_idempotent() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        val engine = f.engine(backgroundScope, catchUpInterval = 5.minutes)

        engine.start()
        engine.start()
        runCurrent()
        val afterStart = f.connectivity.isOnlineChecks

        advanceTimeBy(6.minutes)
        runCurrent()

        assertEquals(1, f.connectivity.isOnlineChecks - afterStart, "one periodic loop, not two")
    }

    // ─── lists read page by page, removals only after a complete read (ADR-73) ──

    private fun Fixture.seedServerProjects(count: Int) =
        (1..count).forEach { backend.seed(ServerProject(id = it.toLong(), name = "Chantier $it")) }

    private fun Fixture.seedServerStages(projectId: Long, count: Int) =
        (1..count).forEach { backend.seedStage(ServerStage(id = 1_000L + it, projectId = projectId, name = "Étape $it")) }

    @Test
    fun more_than_twenty_projects_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.seedServerProjects(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertEquals((1L..25L).toSet(), f.dao.findAll().mapNotNull { it.serverId }.toSet())

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertEquals((1L..25L).toSet(), f.dao.findAll().mapNotNull { it.serverId }.toSet())
    }

    @Test
    fun more_than_a_hundred_projects_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.seedServerProjects(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals((1L..230L).toSet(), f.dao.findAll().mapNotNull { it.serverId }.toSet())
        assertEquals(230, f.dao.findAll().size)
    }

    @Test
    fun more_than_twenty_stages_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        f.seedServerStages(projectId = 7, count = 25)
        f.dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals(25, f.stageDao.findForProject("p7").size)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals((1_001L..1_025L).toSet(), f.stageDao.findForProject("p7").mapNotNull { it.serverId }.toSet())
    }

    @Test
    fun more_than_a_hundred_stages_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        f.seedServerStages(projectId = 7, count = 230)
        f.dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((1_001L..1_230L).toSet(), f.stageDao.findForProject("p7").mapNotNull { it.serverId }.toSet())
        assertEquals(230, f.stageDao.findForProject("p7").size)
    }

    @Test
    fun project_and_stage_lists_are_requested_a_hundred_at_a_time_sorted_by_id() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        f.dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val listQueries = mutableMapOf<String, String>()
        f.backend.beforeHandle = { request ->
            val path = request.url.encodedPath.removePrefix("/api/v1")
            if (path == "/projects" || path == "/projects/7/stages") {
                listQueries[path] = listOf("page", "size", "sort").joinToString("&") { "$it=${request.url.parameters[it]}" }
            }
        }
        val engine = f.engine(backgroundScope)

        engine.syncNow()
        engine.syncProject("p7")

        assertEquals("page=0&size=100&sort=id,asc", listQueries["/projects"])
        assertEquals("page=0&size=100&sort=id,asc", listQueries["/projects/7/stages"])
    }

    @Test
    fun a_project_really_deleted_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = Fixture()
        f.seedServerProjects(230)
        f.dao.upsert(localProject("gone", serverId = 9_999, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertNull(f.dao.findByLocalId("gone"))
        assertEquals(230, f.dao.findAll().size)
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_stage_really_deleted_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        f.seedServerStages(projectId = 7, count = 230)
        f.dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("gone", projectLocalId = "p7", serverId = 9_999, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertNull(f.stageDao.findByLocalId("gone"))
        assertEquals(230, f.stageDao.findForProject("p7").size)
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_project_created_on_the_server_while_the_list_is_read_skips_the_removal_without_failing() = runTest {
        val f = Fixture()
        f.seedServerProjects(150)
        f.dao.upsert(localProject("gone", serverId = 9_999, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith("/projects") && request.url.parameters["page"] == "1" && f.backend.projects.size == 150) {
                f.backend.seed(ServerProject(id = 151, name = "Créé pendant la lecture"))
            }
        }
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertNotNull(f.dao.findByLocalId("gone"), "the total changed during the read: nothing is removed")
        assertEquals(SyncState.Idle, f.syncState.state.value)
        assertEquals((1L..151L).toSet() + 9_999L, f.dao.findAll().mapNotNull { it.serverId }.toSet())
        assertEquals(listOf("sync: incomplete read of projects (TOTAL_MISMATCH), 151 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertNull(f.dao.findByLocalId("gone"), "the next complete read removes it")
        assertEquals(1, f.logged.size)
    }

    @Test
    fun a_stage_created_on_the_server_while_the_list_is_read_skips_the_removal_without_failing() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        f.seedServerStages(projectId = 7, count = 150)
        f.dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("gone", projectLocalId = "p7", serverId = 9_999, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith("/stages") && request.url.parameters["page"] == "1" && f.backend.stages.size == 150) {
                f.backend.seedStage(ServerStage(id = 5_000, projectId = 7, name = "Créée pendant la lecture"))
            }
        }
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertNotNull(f.stageDao.findByLocalId("gone"))
        assertEquals(152, f.stageDao.findForProject("p7").size)
        assertEquals(listOf("sync: incomplete read of stages of project 7 (TOTAL_MISMATCH), 151 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertNull(f.stageDao.findByLocalId("gone"))
    }

    @Test
    fun a_page_of_projects_that_fails_after_the_first_keeps_every_local_project_and_the_pass_succeeds() = runTest {
        val f = Fixture()
        f.seedServerProjects(150)
        (101L..150L).forEach {
            f.dao.upsert(localProject("local-$it", serverId = it, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        }
        f.backend.listPageFailure = 1 to io.ktor.http.HttpStatusCode.InternalServerError
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(150, f.dao.findAll().size, "the first hundred pulled, the fifty unread ones kept")
        assertEquals(listOf("sync: incomplete read of projects (ERROR), 100 read, local removal skipped"), f.logged)
    }

    @Test
    fun a_first_page_of_projects_that_fails_still_fails_the_pass() = runTest {
        val f = Fixture()
        f.seedServerProjects(3)
        f.backend.listPageFailure = 0 to io.ktor.http.HttpStatusCode.InternalServerError
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Failed>(engine.syncNow())
    }

    @Test
    fun a_local_project_that_is_not_synced_is_never_removed_by_a_complete_read() = runTest {
        val f = Fixture()
        f.seedServerProjects(25)
        f.dao.upsert(localProject("editing", serverId = 9_001, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.dao.upsert(localProject("refused", serverId = 9_002, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
        f.dao.upsert(localProject("orphan", serverId = 9_003, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        f.backend.goneOnServer += Regex("""/projects/9001$""")
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNotNull(f.dao.findByLocalId("editing"))
        assertEquals(SyncStatus.CONFLICTED, f.dao.findByLocalId("refused")!!.syncStatus)
        assertEquals(SyncError.REJECTED, f.dao.findByLocalId("refused")!!.lastSyncError)
        assertNotNull(f.dao.findByLocalId("orphan"))
        assertEquals(28, f.dao.findAll().size)
    }

    @Test
    fun a_local_stage_that_is_not_synced_is_never_removed_by_a_complete_read() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        f.seedServerStages(projectId = 7, count = 25)
        f.dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.stageDao.upsert(localStage("editing", projectLocalId = "p7", serverId = 9_001, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.stageDao.upsert(localStage("refused", projectLocalId = "p7", serverId = 9_002, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
        f.stageDao.upsert(localStage("orphan", projectLocalId = "p7", serverId = 9_003, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        f.backend.goneOnServer += Regex("""/stages/9001$""")
        val engine = f.engine(backgroundScope)

        engine.syncProject("p7")

        assertNotNull(f.stageDao.findByLocalId("editing"))
        assertEquals(SyncError.REJECTED, f.stageDao.findByLocalId("refused")!!.lastSyncError)
        assertNotNull(f.stageDao.findByLocalId("orphan"))
        assertEquals(28, f.stageDao.findForProject("p7").size)
    }

    // ─── day logs and materials read page by page (ADR-73, slice 2) ─────────

    private suspend fun Fixture.siteSeven() {
        backend.seed(ServerProject(id = 7, name = "Villa Vidal"))
        backend.seedStage(ServerStage(id = 70, projectId = 7, name = "Gros œuvre"))
        dao.upsert(localProject("p7", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        stageDao.upsert(localStage("st70", projectLocalId = "p7", serverId = 70, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    private fun Fixture.seedServerMaterials(count: Int) =
        (1..count).forEach { backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 2_000L + it, projectId = 7, name = "Matériau $it", unit = "u")) }

    private fun dayNumber(index: Int): String {
        val year = 2000 + index / 336
        val month = (index / 28 % 12 + 1).toString().padStart(2, '0')
        val day = (index % 28 + 1).toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun Fixture.seedServerLogs(count: Int) =
        (1..count).forEach { backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 3_000L + it, stageId = 70, date = dayNumber(it))) }

    @Test
    fun more_than_twenty_materials_are_all_pulled_and_stay_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMaterials(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals(25, f.materialDao.findForProject("p7").size)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals((2_001L..2_025L).toSet(), f.materialDao.findForProject("p7").mapNotNull { it.serverId }.toSet())
    }

    @Test
    fun more_than_a_hundred_materials_are_all_pulled_and_stay_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMaterials(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((2_001L..2_230L).toSet(), f.materialDao.findForProject("p7").mapNotNull { it.serverId }.toSet())
        assertEquals(230, f.materialDao.findForProject("p7").size)
    }

    @Test
    fun more_than_twenty_day_logs_are_all_pulled_and_stay_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerLogs(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals(25, f.dailyLogDao.findForStage("st70").size)

        assertIs<SyncOutcome.Synced>(engine.syncStage("st70"))
        assertEquals((3_001L..3_025L).toSet(), f.dailyLogDao.findForStage("st70").mapNotNull { it.serverId }.toSet())
    }

    @Test
    fun more_than_a_hundred_day_logs_are_all_pulled_and_stay_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerLogs(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertIs<SyncOutcome.Synced>(engine.syncStage("st70"))

        assertEquals((3_001L..3_230L).toSet(), f.dailyLogDao.findForStage("st70").mapNotNull { it.serverId }.toSet())
        assertEquals(230, f.dailyLogDao.findForStage("st70").size)
    }

    @Test
    fun a_server_material_of_the_same_name_beyond_the_twentieth_is_adopted() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMaterials(30)
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 9_999, projectId = 7, name = "Sable", unit = "t"))
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m-sand", projectLocalId = "p7", name = "Sable", unit = "tonne"))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val sand = f.materialDao.findByLocalId("m-sand")!!
        assertEquals(9_999L, sand.serverId)
        assertEquals(SyncStatus.SYNCED, sand.syncStatus)
        assertEquals("t", sand.unit)
    }

    @Test
    fun a_duplicate_material_whose_namesake_was_not_found_in_an_incomplete_read_stays_pending_then_is_adopted() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMaterials(150)
        f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 9_999, projectId = 7, name = "Sable", unit = "t"))
        f.materialDao.upsert(com.dmb.chantiertracker.support.localMaterial("m-sand", projectLocalId = "p7", name = "Sable", unit = "tonne"))
        f.backend.listPageFailure = 1 to io.ktor.http.HttpStatusCode.InternalServerError
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val waiting = f.materialDao.findByLocalId("m-sand")!!
        assertEquals(SyncStatus.PENDING, waiting.syncStatus, "not REJECTED: the read that found no namesake was incomplete")
        assertEquals(PendingOp.CREATE, waiting.pendingOp)
        assertNull(waiting.serverId)
        assertNull(waiting.lastSyncError)
        assertEquals(listOf("sync: incomplete read of materials of project 7 (ERROR), 100 read"), f.logged)

        f.backend.listPageFailure = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val adopted = f.materialDao.findByLocalId("m-sand")!!
        assertEquals(9_999L, adopted.serverId)
        assertEquals(SyncStatus.SYNCED, adopted.syncStatus)
    }

    @Test
    fun a_material_created_on_the_server_while_the_list_is_read_is_logged_and_the_pass_succeeds() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMaterials(150)
        f.backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith("/materials") && request.url.parameters["page"] == "1" && f.backend.materials.size == 150) {
                f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 5_000, projectId = 7, name = "Créé pendant la lecture", unit = "u"))
            }
        }
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals(151, f.materialDao.findForProject("p7").size)
        assertEquals(listOf("sync: incomplete read of materials of project 7 (TOTAL_MISMATCH), 151 read"), f.logged)
    }

    @Test
    fun a_page_of_day_logs_that_fails_after_the_first_keeps_what_was_read_and_the_pass_succeeds() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerLogs(150)
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("known", stageLocalId = "st70", date = dayNumber(150), serverId = 3_150))
        f.backend.listPageFailure = 1 to io.ktor.http.HttpStatusCode.InternalServerError
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncStage("st70"))

        assertEquals(101, f.dailyLogDao.findForStage("st70").size, "the first hundred pulled, the unread one kept")
        assertNotNull(f.dailyLogDao.findByLocalId("known"))
        assertEquals(listOf("sync: incomplete read of day logs of stage 70 (ERROR), 100 read"), f.logged)
    }

    @Test
    fun a_complete_read_of_materials_never_removes_a_local_material() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMaterials(25)
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("orphan", projectLocalId = "p7", name = "Orphelin")
                .copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER),
        )
        f.materialDao.upsert(
            com.dmb.chantiertracker.support.localMaterial("unlisted", projectLocalId = "p7", name = "Hors liste", serverId = 9_001, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals(SyncError.DELETED_ON_SERVER, f.materialDao.findByLocalId("orphan")!!.lastSyncError)
        assertNotNull(f.materialDao.findByLocalId("unlisted"))
        assertEquals(27, f.materialDao.findForProject("p7").size)
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_complete_read_of_day_logs_never_removes_a_local_day() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerLogs(25)
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("local-only", stageLocalId = "st70", date = "2026-09-05"))
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("unlisted", stageLocalId = "st70", date = "2026-09-06", serverId = 9_001))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncStage("st70"))

        assertNull(f.dailyLogDao.findByLocalId("local-only")!!.serverId)
        assertNotNull(f.dailyLogDao.findByLocalId("unlisted"))
        assertEquals(27, f.dailyLogDao.findForStage("st70").size)
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun material_and_day_log_lists_are_requested_a_hundred_at_a_time_sorted_by_id() = runTest {
        val f = Fixture()
        f.siteSeven()
        val listQueries = mutableMapOf<String, String>()
        f.backend.beforeHandle = { request ->
            val path = request.url.encodedPath.removePrefix("/api/v1")
            if (path == "/projects/7/materials" || path == "/stages/70/logs") {
                listQueries[path] = listOf("page", "size", "sort").joinToString("&") { "$it=${request.url.parameters[it]}" }
            }
        }
        val engine = f.engine(backgroundScope)

        engine.syncProject("p7")

        assertEquals("page=0&size=100&sort=id,asc", listQueries["/projects/7/materials"])
        assertEquals("page=0&size=100&sort=id,asc", listQueries["/stages/70/logs"])
    }

    // ─── lines and attachments read page by page (ADR-73, slice 3) ──────────

    private val pageTwoFails = 1 to io.ktor.http.HttpStatusCode.InternalServerError

    private fun Fixture.seedServerPurchaseLines(count: Int) =
        (1..count).forEach { backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 5_000L + it, entryId = 900, materialId = 7, quantity = 1.0, unitPrice = 2.0)) }

    private fun Fixture.seedServerConsumptionLines(count: Int) =
        (1..count).forEach { backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 6_000L + it, entryId = 901, materialId = 7, quantity = 1.0)) }

    private fun Fixture.seedServerAttachments(count: Int) =
        (1..count).forEach { backend.seedAttachment(com.dmb.chantiertracker.support.ServerAttachment(id = 7_000L + it, entryId = 900)) }

    private suspend fun Fixture.syncedPurchaseLine(localId: String, serverId: Long) =
        purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine(localId, entryLocalId = "e900", materialLocalId = "m7", serverId = serverId, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    private suspend fun Fixture.syncedConsumptionLine(localId: String, serverId: Long) =
        consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine(localId, entryLocalId = "e901", materialLocalId = "m7", serverId = serverId, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    private suspend fun Fixture.syncedAttachment(localId: String, serverId: Long): String {
        val path = fileStore.save(byteArrayOf(4, 2), "$localId.jpg")
        attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment(localId, entryLocalId = "e900", localPath = path, serverId = serverId, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        return path
    }

    private fun Fixture.addOneWhilePageTwoIsRead(pathSuffix: String, sizeNow: () -> Int, add: () -> Unit) {
        backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith(pathSuffix) && request.url.parameters["page"] == "1" && sizeNow() == 150) add()
        }
    }

    @Test
    fun more_than_twenty_purchase_lines_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerPurchaseLines(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals(25, f.purchaseLineDao.findForEntry("e900").size)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals((5_001L..5_025L).toSet(), f.purchaseLineDao.findForEntry("e900").mapNotNull { it.serverId }.toSet())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun more_than_a_hundred_purchase_lines_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerPurchaseLines(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals(230, f.purchaseLineDao.findForEntry("e900").size)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals((5_001L..5_230L).toSet(), f.purchaseLineDao.findForEntry("e900").mapNotNull { it.serverId }.toSet())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun one_of_the_purchase_lines_really_deleted_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerPurchaseLines(230)
        f.syncedPurchaseLine("gone", serverId = 9_999)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNull(f.purchaseLineDao.findByLocalId("gone"))
        assertEquals(230, f.purchaseLineDao.findForEntry("e900").size)
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_second_page_of_purchase_lines_that_fails_after_a_first_one_that_succeeded_removes_nothing() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerPurchaseLines(150)
        (5000+101L..5000+150L).map { f.syncedPurchaseLine("local-$it", serverId = it) }
        f.backend.listPageFailure = pageTwoFails
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertEquals(150, f.purchaseLineDao.findForEntry("e900").size, "the first hundred pulled, the fifty unread ones kept")
        assertEquals(listOf("sync: incomplete read of purchase lines of entry 900 (ERROR), 100 read, local removal skipped"), f.logged)
    }

    @Test
    fun one_of_the_purchase_lines_created_on_the_server_while_the_list_is_read_skips_the_removal_without_failing() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerPurchaseLines(150)
        f.syncedPurchaseLine("gone", serverId = 9_999)
        f.addOneWhilePageTwoIsRead("purchase-lines", { f.backend.purchaseLines.size }) { f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 5_900, entryId = 900, materialId = 7, quantity = 1.0, unitPrice = 2.0)) }
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNotNull(f.purchaseLineDao.findByLocalId("gone"), "the total changed during the read: nothing is removed")
        assertEquals(152, f.purchaseLineDao.findForEntry("e900").size)
        assertEquals(listOf("sync: incomplete read of purchase lines of entry 900 (TOTAL_MISMATCH), 151 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNull(f.purchaseLineDao.findByLocalId("gone"), "the next complete read removes it")
        assertEquals(1, f.logged.size)
    }

    @Test
    fun one_of_the_local_purchase_lines_that_is_not_synced_is_never_removed_by_a_complete_read() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerPurchaseLines(25)
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("editing", entryLocalId = "e900", materialLocalId = "m7", serverId = 9_001, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("refused", entryLocalId = "e900", materialLocalId = "m7", serverId = 9_002, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.REJECTED))
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("orphan", entryLocalId = "e900", materialLocalId = "m7", serverId = 9_003, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.DELETED_ON_SERVER))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNotNull(f.purchaseLineDao.findByLocalId("editing"))
        assertEquals(SyncError.REJECTED, f.purchaseLineDao.findByLocalId("refused")!!.lastSyncError)
        assertNotNull(f.purchaseLineDao.findByLocalId("orphan"))
        assertEquals(28, f.purchaseLineDao.findForEntry("e900").size)
    }

    @Test
    fun more_than_twenty_consumption_lines_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerConsumptionLines(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals(25, f.consumptionLineDao.findForEntry("e901").size)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals((6_001L..6_025L).toSet(), f.consumptionLineDao.findForEntry("e901").mapNotNull { it.serverId }.toSet())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun more_than_a_hundred_consumption_lines_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerConsumptionLines(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals(230, f.consumptionLineDao.findForEntry("e901").size)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals((6_001L..6_230L).toSet(), f.consumptionLineDao.findForEntry("e901").mapNotNull { it.serverId }.toSet())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun one_of_the_consumption_lines_really_deleted_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerConsumptionLines(230)
        f.syncedConsumptionLine("gone", serverId = 9_999)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNull(f.consumptionLineDao.findByLocalId("gone"))
        assertEquals(230, f.consumptionLineDao.findForEntry("e901").size)
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_second_page_of_consumption_lines_that_fails_after_a_first_one_that_succeeded_removes_nothing() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerConsumptionLines(150)
        (6000+101L..6000+150L).map { f.syncedConsumptionLine("local-$it", serverId = it) }
        f.backend.listPageFailure = pageTwoFails
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertEquals(150, f.consumptionLineDao.findForEntry("e901").size, "the first hundred pulled, the fifty unread ones kept")
        assertEquals(listOf("sync: incomplete read of consumption lines of entry 901 (ERROR), 100 read, local removal skipped"), f.logged)
    }

    @Test
    fun one_of_the_consumption_lines_created_on_the_server_while_the_list_is_read_skips_the_removal_without_failing() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerConsumptionLines(150)
        f.syncedConsumptionLine("gone", serverId = 9_999)
        f.addOneWhilePageTwoIsRead("consumption-lines", { f.backend.consumptionLines.size }) { f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 6_900, entryId = 901, materialId = 7, quantity = 1.0)) }
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNotNull(f.consumptionLineDao.findByLocalId("gone"), "the total changed during the read: nothing is removed")
        assertEquals(152, f.consumptionLineDao.findForEntry("e901").size)
        assertEquals(listOf("sync: incomplete read of consumption lines of entry 901 (TOTAL_MISMATCH), 151 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNull(f.consumptionLineDao.findByLocalId("gone"), "the next complete read removes it")
        assertEquals(1, f.logged.size)
    }

    @Test
    fun one_of_the_local_consumption_lines_that_is_not_synced_is_never_removed_by_a_complete_read() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerConsumptionLines(25)
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("editing", entryLocalId = "e901", materialLocalId = "m7", serverId = 9_001, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("refused", entryLocalId = "e901", materialLocalId = "m7", serverId = 9_002, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.REJECTED))
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("orphan", entryLocalId = "e901", materialLocalId = "m7", serverId = 9_003, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.DELETED_ON_SERVER))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNotNull(f.consumptionLineDao.findByLocalId("editing"))
        assertEquals(SyncError.REJECTED, f.consumptionLineDao.findByLocalId("refused")!!.lastSyncError)
        assertNotNull(f.consumptionLineDao.findByLocalId("orphan"))
        assertEquals(28, f.consumptionLineDao.findForEntry("e901").size)
    }

    @Test
    fun more_than_twenty_attachments_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerAttachments(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals(25, f.attachmentDao.findForEntry("e900").size)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals((7_001L..7_025L).toSet(), f.attachmentDao.findForEntry("e900").mapNotNull { it.serverId }.toSet())
        assertEquals(25, f.fileStore.storedPaths.size)
        assertTrue(f.fileStore.deletedPaths.isEmpty())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun more_than_a_hundred_attachments_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerAttachments(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals(230, f.attachmentDao.findForEntry("e900").size)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))
        assertEquals((7_001L..7_230L).toSet(), f.attachmentDao.findForEntry("e900").mapNotNull { it.serverId }.toSet())
        assertEquals(230, f.fileStore.storedPaths.size)
        assertTrue(f.fileStore.deletedPaths.isEmpty())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun one_of_the_attachments_really_deleted_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerAttachments(230)
        val goneFile = f.syncedAttachment("gone", serverId = 9_999)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNull(f.attachmentDao.findByLocalId("gone"))
        assertEquals(230, f.attachmentDao.findForEntry("e900").size)
        assertEquals(listOf(goneFile), f.fileStore.deletedPaths, "only the file of the attachment the server no longer has")
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_second_page_of_attachments_that_fails_after_a_first_one_that_succeeded_removes_nothing() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerAttachments(150)
        val unreadFiles = (7000+101L..7000+150L).map { f.syncedAttachment("local-$it", serverId = it) }
        f.backend.listPageFailure = pageTwoFails
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertEquals(150, f.attachmentDao.findForEntry("e900").size, "the first hundred pulled, the fifty unread ones kept")
        assertTrue(f.fileStore.deletedPaths.isEmpty(), "no file is deleted after an incomplete read")
        assertTrue(f.fileStore.storedPaths.containsAll(unreadFiles))
        assertEquals(150, f.fileStore.storedPaths.size)
        assertEquals(listOf("sync: incomplete read of attachments of entry 900 (ERROR), 100 read, local removal skipped"), f.logged)
    }

    @Test
    fun one_of_the_attachments_created_on_the_server_while_the_list_is_read_skips_the_removal_without_failing() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerAttachments(150)
        val goneFile = f.syncedAttachment("gone", serverId = 9_999)
        f.addOneWhilePageTwoIsRead("attachments", { f.backend.attachments.size }) { f.backend.seedAttachment(com.dmb.chantiertracker.support.ServerAttachment(id = 7_900, entryId = 900)) }
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNotNull(f.attachmentDao.findByLocalId("gone"), "the total changed during the read: nothing is removed")
        assertEquals(152, f.attachmentDao.findForEntry("e900").size)
        assertTrue(f.fileStore.deletedPaths.isEmpty(), "no file is deleted after an incomplete read")
        assertTrue(goneFile in f.fileStore.storedPaths)
        assertEquals(listOf("sync: incomplete read of attachments of entry 900 (TOTAL_MISMATCH), 151 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNull(f.attachmentDao.findByLocalId("gone"), "the next complete read removes it")
        assertEquals(listOf(goneFile), f.fileStore.deletedPaths)
        assertEquals(1, f.logged.size)
    }

    @Test
    fun a_local_attachment_that_is_not_synced_is_never_removed_by_a_complete_read_and_keeps_its_file() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedServerAttachments(25)
        suspend fun fileOf(localId: String) = f.fileStore.save(byteArrayOf(4, 2), "$localId.jpg")
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("sending", entryLocalId = "e900", localPath = fileOf("sending"), serverId = null, pendingOp = PendingOp.CREATE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.REJECTED))
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("refused", entryLocalId = "e900", localPath = fileOf("refused"), serverId = 9_002, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.REJECTED))
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("orphan", entryLocalId = "e900", localPath = fileOf("orphan"), serverId = 9_003, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = SyncError.DELETED_ON_SERVER))
        f.backend.attachmentUploadRejection = io.ktor.http.HttpStatusCode.Forbidden to "Limite de photos atteinte."
        val localFiles = f.fileStore.storedPaths.toSet()
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertNotNull(f.attachmentDao.findByLocalId("sending"))
        assertNotNull(f.attachmentDao.findByLocalId("refused"))
        assertNotNull(f.attachmentDao.findByLocalId("orphan"))
        assertEquals(28, f.attachmentDao.findForEntry("e900").size)
        assertTrue(f.fileStore.deletedPaths.isEmpty())
        assertTrue(f.fileStore.storedPaths.containsAll(localFiles))
    }

    @Test
    fun line_and_attachment_lists_are_requested_a_hundred_at_a_time_sorted_by_id() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        val listQueries = mutableMapOf<String, String>()
        f.backend.beforeHandle = { request ->
            val path = request.url.encodedPath.removePrefix("/api/v1")
            if (request.method == io.ktor.http.HttpMethod.Get && path.startsWith("/entries/")) {
                listQueries[path] = listOf("page", "size", "sort").joinToString("&") { "$it=${request.url.parameters[it]}" }
            }
        }
        val engine = f.engine(backgroundScope)

        engine.syncLog("l800")

        assertEquals(
            mapOf(
                "/entries/900/purchase-lines" to "page=0&size=100&sort=id,asc",
                "/entries/900/attachments" to "page=0&size=100&sort=id,asc",
                "/entries/901/consumption-lines" to "page=0&size=100&sort=id,asc",
            ),
            listQueries,
        )
    }

    // ─── members, invitations and stock read page by page (ADR-73, slice 4) ─

    private fun Fixture.seedServerMembers(count: Int) =
        backend.seedMembers(7, *(1..count).map { ServerMember(userId = 100L + it, name = "Membre $it", email = "m$it@x.dev", role = "SUPERVISOR") }.toTypedArray())

    private fun Fixture.seedServerInvitations(count: Int) =
        (1..count).forEach { backend.seedInvitation(com.dmb.chantiertracker.support.ServerInvitation(id = 1_200L + it, projectId = 7, email = "invite$it@x.dev")) }

    private suspend fun Fixture.memberIds() = dao.observeMembers("p7").first().map { it.userId }.toSet()

    private suspend fun Fixture.invitationIds() = invitationDao.findForProject("p7").map { it.id }.toSet()

    private fun Fixture.seedStockedMaterials(count: Int) =
        (1..count).forEach { n ->
            backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 10_000L + n, projectId = 5, name = "Matériau $n", unit = "u"))
            backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 20_000L + n, entryId = 900, materialId = 10_000L + n, quantity = 4.0, unitPrice = 1.0))
        }

    @Test
    fun more_than_twenty_members_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMembers(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals(25, f.memberIds().size)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals((101L..125L).toSet(), f.memberIds())
    }

    @Test
    fun more_than_a_hundred_members_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMembers(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((101L..330L).toSet(), f.memberIds())
    }

    @Test
    fun a_member_removed_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMembers(230)
        f.dao.upsertMembers(listOf(com.dmb.chantiertracker.data.local.db.ProjectMemberEntity("p7", 9_999, "Parti", "parti@x.dev", "SUPERVISOR")))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((101L..330L).toSet(), f.memberIds())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_second_page_of_members_that_fails_after_a_first_one_that_succeeded_replaces_nothing() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMembers(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        f.backend.listPageFailure = pageTwoFails

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((101L..250L).toSet(), f.memberIds(), "the fifty members of the unread page are kept")
        assertEquals(listOf("sync: incomplete read of members of project 7 (ERROR), 100 read, local removal skipped"), f.logged)
    }

    @Test
    fun a_member_added_on_the_server_while_the_list_is_read_skips_the_replacement_without_failing() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerMembers(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        f.backend.members.getValue(7).removeAll { it.userId == 101L }
        f.backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith("/members") && request.url.parameters["page"] == "1" && f.backend.members.getValue(7).size == 149) {
                f.backend.seedMembers(7, ServerMember(userId = 900, name = "Arrivé pendant la lecture", email = "new@x.dev", role = "SUPERVISOR"))
            }
        }

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertTrue(101L in f.memberIds(), "the total changed during the read: the removed member is kept for now")
        assertTrue(900L in f.memberIds())
        assertEquals(listOf("sync: incomplete read of members of project 7 (TOTAL_MISMATCH), 150 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((102L..250L).toSet() + 900L, f.memberIds(), "the next complete read replaces the list")
    }

    @Test
    fun more_than_twenty_invitations_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerInvitations(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals(25, f.invitationIds().size)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertEquals((1_201L..1_225L).toSet(), f.invitationIds())
    }

    @Test
    fun more_than_a_hundred_invitations_are_all_pulled_and_none_disappears_on_the_next_pass() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerInvitations(230)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((1_201L..1_430L).toSet(), f.invitationIds())
    }

    @Test
    fun an_invitation_cancelled_on_the_server_is_still_removed_when_the_list_spans_several_pages() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerInvitations(230)
        f.invitationDao.upsertAll(listOf(com.dmb.chantiertracker.support.localInvitation(9_999, projectLocalId = "p7")))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((1_201L..1_430L).toSet(), f.invitationIds())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_second_page_of_invitations_that_fails_after_a_first_one_that_succeeded_replaces_nothing() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerInvitations(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        f.backend.listPageFailure = pageTwoFails

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((1_201L..1_350L).toSet(), f.invitationIds(), "the fifty invitations of the unread page are kept")
        assertEquals(listOf("sync: incomplete read of invitations of project 7 (ERROR), 100 read, local removal skipped"), f.logged)
    }

    @Test
    fun an_invitation_sent_on_the_server_while_the_list_is_read_skips_the_replacement_without_failing() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerInvitations(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        f.backend.invitations.removeAll { it.id == 1_201L }
        f.backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith("/invitations") && request.url.parameters["page"] == "1" && f.backend.invitations.size == 149) {
                f.backend.seedInvitation(com.dmb.chantiertracker.support.ServerInvitation(id = 1_900, projectId = 7, email = "pendant@x.dev"))
            }
        }

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertTrue(1_201L in f.invitationIds(), "the total changed during the read: the cancelled invitation is kept for now")
        assertTrue(1_900L in f.invitationIds())
        assertEquals(listOf("sync: incomplete read of invitations of project 7 (TOTAL_MISMATCH), 150 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals((1_202L..1_350L).toSet() + 1_900L, f.invitationIds())
    }

    @Test
    fun the_invitation_cache_is_still_cleared_when_the_first_page_answers_403() = runTest {
        val f = Fixture()
        f.siteSeven()
        f.seedServerInvitations(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))
        f.backend.invitationsForbidden = true

        assertIs<SyncOutcome.Synced>(engine.syncProject("p7"))

        assertEquals(emptySet(), f.invitationIds())
    }

    @Test
    fun member_and_invitation_lists_are_requested_a_hundred_at_a_time_sorted_by_id() = runTest {
        val f = Fixture()
        f.siteSeven()
        val listQueries = mutableMapOf<String, String>()
        f.backend.beforeHandle = { request ->
            val path = request.url.encodedPath.removePrefix("/api/v1")
            if (path == "/projects/7/members" || path == "/projects/7/invitations" || path == "/projects/7/stock") {
                listQueries[path] = listOf("page", "size", "sort").joinToString("&") { "$it=${request.url.parameters[it]}" }
            }
        }
        val engine = f.engine(backgroundScope)

        engine.syncProject("p7")

        assertEquals("page=0&size=100&sort=id,asc", listQueries["/projects/7/members"])
        assertEquals("page=0&size=100&sort=id,asc", listQueries["/projects/7/invitations"])
        assertEquals("page=0&size=100&sort=id,asc", listQueries["/projects/7/stock"])
    }

    @Test
    fun more_than_twenty_stock_counters_are_all_loaded_and_stay_on_the_next_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedStockedMaterials(25)
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        assertEquals(25, f.stockDao.counters.value.size)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        assertEquals((10_001L..10_025L).toSet(), f.stockDao.counters.value.keys.map { it.second }.toSet())
        assertTrue(f.logged.isEmpty())
    }

    @Test
    fun a_second_page_of_stock_that_fails_after_a_first_one_that_succeeded_keeps_the_previous_counters() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedStockedMaterials(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        val loadedAt = f.stockDao.findSnapshot("p5")!!.refreshedAt
        f.backend.purchaseLines.first { it.materialId == 10_001L }.quantity = 99.0
        f.clock.advanceBy(60_000)
        f.backend.listPageFailure = pageTwoFails

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        assertEquals(150, f.stockDao.counters.value.size, "the fifty counters of the unread page are kept")
        assertEquals(4.0, f.stockDao.counter("p5", 10_001)!!.quantityIn, "nothing is replaced, not even what the first page said")
        assertEquals(loadedAt, f.stockDao.findSnapshot("p5")!!.refreshedAt, "« Stock au … » still shows the last complete load")
        assertEquals(listOf("p5"), f.stockDao.findProjectsNeedingRefresh(), "reloaded at the next pass")
        assertEquals(
            listOf(
                "sync: incomplete read of materials of project 5 (ERROR), 100 read",
                "sync: incomplete read of stock of project 5 (ERROR), 100 read, local removal skipped",
            ),
            f.logged,
            "the stock is read once per pass, not again at its end",
        )

        f.backend.listPageFailure = null
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(99.0, f.stockDao.counter("p5", 10_001)!!.quantityIn)
        assertTrue(f.stockDao.findProjectsNeedingRefresh().isEmpty())
    }

    @Test
    fun a_stock_counter_created_on_the_server_while_the_stock_is_read_keeps_the_previous_counters() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedStockedMaterials(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        f.backend.beforeHandle = { request ->
            if (request.url.encodedPath.endsWith("/stock") && request.url.parameters["page"] == "1" && f.backend.materials.none { it.id == 19_000L }) {
                f.backend.seedMaterial(com.dmb.chantiertracker.support.ServerMaterial(id = 19_000, projectId = 5, name = "Arrivé pendant la lecture", unit = "u"))
                f.backend.seedPurchaseLine(com.dmb.chantiertracker.support.ServerPurchaseLine(id = 29_000, entryId = 900, materialId = 19_000, quantity = 1.0, unitPrice = 1.0))
            }
        }

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        assertEquals(150, f.stockDao.counters.value.size)
        assertNull(f.stockDao.counter("p5", 19_000))
        assertEquals(listOf("sync: incomplete read of stock of project 5 (TOTAL_MISMATCH), 151 read, local removal skipped"), f.logged)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(1.0, f.stockDao.counter("p5", 19_000)!!.quantityIn, "the next complete read replaces the counters")
        assertEquals(151, f.stockDao.counters.value.size)
    }

    @Test
    fun a_stock_never_loaded_stays_never_loaded_after_an_incomplete_read() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.seedStockedMaterials(150)
        f.backend.listPageFailure = pageTwoFails
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        assertNull(f.stockDao.findSnapshot("p5"), "no partial stock is shown as if it were the server's")
        assertTrue(f.stockDao.counters.value.isEmpty())
    }

    @Test
    fun a_pending_local_line_still_counts_on_top_of_the_counters_kept_after_an_incomplete_read() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.seedStockedMaterials(150)
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        f.backend.purchaseLines.first { it.id == 5000L }.quantity = 50.0
        f.backend.listPageFailure = pageTwoFails
        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        f.connectivity.setOnline(false)

        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-offline", entryLocalId = "e900", materialLocalId = "m7", quantity = 3.0))

        assertEquals(15.0, f.cementAvailable(), "last complete server counter (12) plus the unsent line (3), ADR-71")
        assertNotNull(f.purchaseLineDao.findByLocalId("pl-offline"))
    }

    // ─── refusals: what is sent again and what waits for the user (ADR-74) ──

    private fun Fixture.sent(method: String, pathSuffix: String) =
        backend.receivedMethods.count { it.startsWith("$method ") && it.endsWith(pathSuffix) }

    @Test
    fun a_refused_creation_is_sent_again_at_every_pass_and_leaves_once_the_refusal_is_lifted() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 2.0)
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("cl-too-much", entryLocalId = "e901", materialLocalId = "m7", quantity = 5.0))
        f.backend.lineWriteConflict = true
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(2, f.sent("POST", "/entries/901/consumption-lines"))
        assertEquals(SyncStatus.CONFLICTED, f.consumptionLineDao.findByLocalId("cl-too-much")!!.syncStatus)

        f.backend.lineWriteConflict = false
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(SyncStatus.SYNCED, f.consumptionLineDao.findByLocalId("cl-too-much")!!.syncStatus)
    }

    @Test
    fun a_refused_update_of_a_purchase_line_is_sent_once_and_then_waits_for_the_user() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl5000", entryLocalId = "e900", materialLocalId = "m7", quantity = 3.0, serverId = 5000, pendingOp = PendingOp.UPDATE)
                .copy(serverQuantity = 12.0),
        )
        f.backend.updateStatus = io.ktor.http.HttpStatusCode.Conflict
        f.backend.updateRefusalCode = "STOCK_CONSUMED"
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertEquals(1, f.sent("PATCH", "/purchase-lines/5000"), "sent once, not at every pass")
        val line = f.purchaseLineDao.findByLocalId("pl5000")!!
        assertEquals(SyncStatus.CONFLICTED, line.syncStatus)
        assertEquals(PendingOp.UPDATE, line.pendingOp)
        assertEquals(SyncError.UPDATE_REFUSED, line.lastSyncError)
        assertEquals(3.0, line.quantity, "what the user typed is kept")
        assertEquals("STOCK_CONSUMED", line.serverErrorCode)
        assertEquals(12.0, f.cementAvailable(), "the stock follows the server, ADR-71")
    }

    @Test
    fun a_refused_update_of_a_consumption_line_is_sent_once_and_then_waits_for_the_user() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.backend.seedConsumptionLine(com.dmb.chantiertracker.support.ServerConsumptionLine(id = 6000, entryId = 901, materialId = 7, quantity = 2.0))
        f.consumptionLineDao.upsert(
            com.dmb.chantiertracker.support.localConsumptionLine("cl6000", entryLocalId = "e901", materialLocalId = "m7", quantity = 50.0, serverId = 6000, pendingOp = PendingOp.UPDATE)
                .copy(serverQuantity = 2.0),
        )
        f.backend.updateStatus = io.ktor.http.HttpStatusCode.Conflict
        f.backend.updateRefusalCode = "INSUFFICIENT_STOCK"
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(1, f.sent("PATCH", "/consumption-lines/6000"))
        assertEquals(SyncError.UPDATE_REFUSED, f.consumptionLineDao.findByLocalId("cl6000")!!.lastSyncError)
        assertEquals(50.0, f.consumptionLineDao.findByLocalId("cl6000")!!.quantity)
    }

    @Test
    fun a_refused_update_of_a_project_a_stage_a_material_or_an_entry_is_sent_once_and_never_fails_the_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.dao.upsert(
            f.dao.findByLocalId("p5")!!.copy(
                name = "Renommé", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING, remoteUpdatedAt = serverMillis("2026-01-01T09:00:00"),
            ),
        )
        f.stageDao.upsert(f.stageDao.findByLocalId("st90")!!.copy(name = "Renommée", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.materialDao.upsert(f.materialDao.findByLocalId("m7")!!.copy(name = "Ciment gris", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.dailyEntryDao.upsert(f.dailyEntryDao.findByLocalId("e900")!!.copy(summary = "Résumé", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING))
        f.backend.updateStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.updateRefusalCode = "PROJECT_INSUFFICIENT_ROLE"
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(listOf(1, 1, 1, 1), listOf(f.sent("PATCH", "/projects/5"), f.sent("PATCH", "/stages/90"), f.sent("PATCH", "/materials/7"), f.sent("PATCH", "/entries/900")))
        assertEquals(SyncError.UPDATE_REFUSED, f.dao.findByLocalId("p5")!!.lastSyncError)
        assertEquals("Renommé", f.dao.findByLocalId("p5")!!.name)
        assertEquals(SyncError.UPDATE_REFUSED, f.stageDao.findByLocalId("st90")!!.lastSyncError)
        assertEquals(SyncError.UPDATE_REFUSED, f.materialDao.findByLocalId("m7")!!.lastSyncError)
        assertEquals(SyncError.UPDATE_REFUSED, f.dailyEntryDao.findByLocalId("e900")!!.lastSyncError)
        assertEquals(
            List(4) { "PROJECT_INSUFFICIENT_ROLE" },
            listOf(f.dao.findByLocalId("p5")!!.serverErrorCode, f.stageDao.findByLocalId("st90")!!.serverErrorCode, f.materialDao.findByLocalId("m7")!!.serverErrorCode, f.dailyEntryDao.findByLocalId("e900")!!.serverErrorCode),
        )
    }

    @Test
    fun a_refused_project_update_does_not_fail_the_pass_and_everything_queued_behind_it_still_leaves() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        f.dao.upsert(
            f.dao.findByLocalId("p5")!!.copy(
                name = "Renommé", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING, remoteUpdatedAt = serverMillis("2026-01-01T09:00:00"),
            ),
        )
        f.stageDao.upsert(localStage("st-new", projectLocalId = "p5", name = "Finitions"))
        f.purchaseLineDao.upsert(com.dmb.chantiertracker.support.localPurchaseLine("pl-new", entryLocalId = "e900", materialLocalId = "m7", quantity = 4.0))
        f.backend.seed(ServerProject(id = 6, name = "Créé ailleurs"))
        f.backend.updateStatus = io.ktor.http.HttpStatusCode.Forbidden
        f.backend.updateRefusalCode = "PROJECT_INSUFFICIENT_ROLE"
        val engine = f.engine(backgroundScope)

        val outcome = engine.syncNow()

        assertIs<SyncOutcome.Synced>(outcome, "one refused project update must not fail the whole pass")
        assertEquals(SyncState.Idle, f.syncState.state.value)
        assertEquals(1, f.sent("PATCH", "/projects/5"))
        val project = f.dao.findByLocalId("p5")!!
        assertEquals(SyncStatus.CONFLICTED, project.syncStatus)
        assertEquals(SyncError.UPDATE_REFUSED, project.lastSyncError)
        assertEquals("PROJECT_INSUFFICIENT_ROLE", project.serverErrorCode)
        assertEquals("Renommé", project.name, "what the user typed is kept")
        assertEquals(SyncStatus.SYNCED, f.stageDao.findByLocalId("st-new")!!.syncStatus, "the stage queued behind the project still leaves")
        assertEquals(SyncStatus.SYNCED, f.purchaseLineDao.findByLocalId("pl-new")!!.syncStatus, "the line queued behind the project still leaves")
        assertTrue(f.dao.findAll().any { it.serverId == 6L }, "the pull still runs after the refusal")
    }

    @Test
    fun a_refused_delete_is_never_sent_again_and_its_mention_survives_the_next_pulls() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl5000", entryLocalId = "e900", materialLocalId = "m7", quantity = 12.0, serverId = 5000, pendingOp = PendingOp.DELETE)
                .copy(serverQuantity = 12.0),
        )
        f.dailyEntryDao.upsert(f.dailyEntryDao.findByLocalId("e901")!!.copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        f.backend.deleteStatus = io.ktor.http.HttpStatusCode.Conflict
        f.backend.deleteRefusalCode = "STOCK_CONSUMED"
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))
        assertIs<SyncOutcome.Synced>(engine.syncLog("l800"))

        assertEquals(1, f.sent("DELETE", "/purchase-lines/5000"))
        assertEquals(1, f.sent("DELETE", "/entries/901"))
        val line = f.purchaseLineDao.findByLocalId("pl5000")!!
        assertEquals(SyncStatus.SYNCED, line.syncStatus)
        assertEquals(PendingOp.NONE, line.pendingOp)
        assertEquals(SyncError.REJECTED, line.lastSyncError, "the « deletion refused » mention is still there after a pull")
        assertEquals("STOCK_CONSUMED", line.serverErrorCode, "and so is its reason")
        assertEquals(SyncError.REJECTED, f.dailyEntryDao.findByLocalId("e901")!!.lastSyncError)
    }

    @Test
    fun a_photo_the_server_finds_too_large_is_refused_once_never_uploaded_again_and_never_fails_the_pass() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        val path = f.fileStore.save(ByteArray(8), "lourde.jpg")
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("a-heavy", entryLocalId = "e900", localPath = path))
        f.backend.attachmentUploadRejection = io.ktor.http.HttpStatusCode.PayloadTooLarge to "Fichier trop volumineux."
        f.backend.attachmentUploadRejectionCode = "ATTACHMENT_TOO_LARGE"
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(1, f.sent("POST", "/entries/900/attachments"), "the file is not uploaded again at every pass")
        val photo = f.attachmentDao.findByLocalId("a-heavy")!!
        assertEquals(SyncStatus.CONFLICTED, photo.syncStatus)
        assertEquals(SyncError.FILE_REFUSED, photo.lastSyncError)
        assertEquals("ATTACHMENT_TOO_LARGE", photo.serverErrorCode)
        assertTrue(path in f.fileStore.storedPaths, "the local file is kept")
    }

    @Test
    fun a_server_error_on_an_upload_is_not_a_file_refusal_and_is_tried_again() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        val path = f.fileStore.save(ByteArray(8), "photo.jpg")
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("a1", entryLocalId = "e900", localPath = path))
        f.backend.attachmentUploadRejection = io.ktor.http.HttpStatusCode.InternalServerError to "Erreur."
        val engine = f.engine(backgroundScope)

        engine.syncNow()
        engine.syncNow()

        assertEquals(2, f.sent("POST", "/entries/900/attachments"))
    }

    @Test
    fun a_file_too_large_refused_by_a_proxy_without_any_code_still_reads_as_a_file_refusal() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = null)
        val path = f.fileStore.save(ByteArray(8), "lourde.jpg")
        f.attachmentDao.upsert(com.dmb.chantiertracker.support.localAttachment("a-heavy", entryLocalId = "e900", localPath = path))
        f.backend.attachmentUploadRejection = io.ktor.http.HttpStatusCode.PayloadTooLarge to "Request Entity Too Large"
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val photo = f.attachmentDao.findByLocalId("a-heavy")!!
        assertEquals(SyncError.FILE_REFUSED, photo.lastSyncError)
        assertEquals("ATTACHMENT_TOO_LARGE", photo.serverErrorCode)
    }

    @Test
    fun the_server_code_of_a_refused_creation_is_kept_and_cleared_once_the_row_is_accepted() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 2.0)
        f.dao.upsert(localProject("p-new", name = "Deuxième projet"))
        f.consumptionLineDao.upsert(com.dmb.chantiertracker.support.localConsumptionLine("cl-too-much", entryLocalId = "e901", materialLocalId = "m7", quantity = 5.0))
        f.backend.planLimitReached = true
        f.backend.lineWriteConflict = true
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val project = f.dao.findByLocalId("p-new")!!
        assertEquals(SyncError.PLAN_LIMIT to "PLAN_LIMIT_EXCEEDED", project.lastSyncError to project.serverErrorCode)
        val line = f.consumptionLineDao.findByLocalId("cl-too-much")!!
        assertEquals(SyncError.REJECTED to "INSUFFICIENT_STOCK", line.lastSyncError to line.serverErrorCode)

        f.backend.planLimitReached = false
        f.backend.lineWriteConflict = false
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        assertEquals(null to null, f.dao.findByLocalId("p-new")!!.let { it.lastSyncError to it.serverErrorCode })
        assertEquals(null to null, f.consumptionLineDao.findByLocalId("cl-too-much")!!.let { it.lastSyncError to it.serverErrorCode })
    }

    @Test
    fun a_refused_update_leaves_once_the_user_saves_it_again() = runTest {
        val f = stockFixture()
        f.siteWithCement(serverPurchase = 12.0)
        f.purchaseLineDao.upsert(
            com.dmb.chantiertracker.support.localPurchaseLine("pl5000", entryLocalId = "e900", materialLocalId = "m7", quantity = 3.0, serverId = 5000, pendingOp = PendingOp.UPDATE)
                .copy(serverQuantity = 12.0),
        )
        f.backend.updateStatus = io.ktor.http.HttpStatusCode.Conflict
        f.backend.updateRefusalCode = "STOCK_CONSUMED"
        val engine = f.engine(backgroundScope)
        assertIs<SyncOutcome.Synced>(engine.syncNow())
        f.backend.updateStatus = null

        assertIs<SyncOutcome.Synced>(engine.syncNow())
        assertEquals(1, f.sent("PATCH", "/purchase-lines/5000"), "a lifted refusal does not send a refused edit again by itself")

        f.purchaseLineDao.upsert(f.purchaseLineDao.findByLocalId("pl5000")!!.copy(quantity = 9.0, syncStatus = SyncStatus.PENDING, lastSyncError = null))
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val line = f.purchaseLineDao.findByLocalId("pl5000")!!
        assertEquals(SyncStatus.SYNCED, line.syncStatus)
        assertEquals(9.0, f.backend.purchaseLines.single { it.id == 5000L }.quantity)
        assertNull(line.serverErrorCode)
    }
}
