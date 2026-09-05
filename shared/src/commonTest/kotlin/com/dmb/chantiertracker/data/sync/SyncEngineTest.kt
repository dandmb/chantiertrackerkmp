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
        val backend: FakeProjectBackend = FakeProjectBackend(),
        val connectivity: FakeConnectivityObserver = FakeConnectivityObserver(),
        val clock: MutableClock = MutableClock(serverMillis("2026-09-02T09:00:00")),
        val syncState: SyncStateHolder = SyncStateHolder(),
        val backgroundSync: FakeBackgroundSync = FakeBackgroundSync(),
    ) {
        var idSeq = 0
        fun engine(scope: CoroutineScope, catchUpInterval: Duration = 15.minutes) = SyncEngine(
            dao = dao,
            api = backend.api(),
            stageDao = stageDao,
            stageApi = backend.stageApi(),
            materialDao = materialDao,
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
            connectivity = connectivity,
            syncState = syncState,
            scope = scope,
            clock = clock,
            newLocalId = { "pulled-${idSeq++}" },
            backgroundSync = backgroundSync,
            catchUpInterval = catchUpInterval,
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
        assertEquals(SyncError.REJECTED, stage.lastSyncError)
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
    fun a_video_attachment_uploaded_from_the_web_is_skipped_on_pull() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa"))
        f.backend.seedStage(ServerStage(id = 90, projectId = 5, name = "S"))
        val log = f.backend.seedLog(com.dmb.chantiertracker.support.ServerLog(id = 800, stageId = 90, date = "2026-09-05"))
        val purchase = f.backend.seedEntry(com.dmb.chantiertracker.support.ServerEntry(id = 900, dailyLogId = 800, type = "PURCHASE"))
        f.backend.seedAttachment(com.dmb.chantiertracker.support.ServerAttachment(id = 1100, entryId = purchase.id, mimeType = "video/mp4", originalName = "clip.mp4"))
        f.dailyLogDao.upsert(com.dmb.chantiertracker.support.localDailyLog("l800", stageLocalId = "st90", date = "2026-09-05", serverId = 800))
        val engine = f.engine(backgroundScope)

        engine.syncLog("l800")

        assertTrue(f.attachmentDao.stored.isEmpty(), "mobile is photo-only — the video is not downloaded")
    }

    @Test
    fun pushing_an_entry_update_the_server_already_removed_drops_the_local_row() = runTest {
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

        assertNull(f.dailyEntryDao.findByLocalId("e1"), "a 404 on the entry PATCH removes the local row")
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
}
