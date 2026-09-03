package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.support.FakeStageDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageRepositoryImplTest {

    private fun repo(
        dao: FakeStageDao = FakeStageDao(),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = StageRepositoryImpl(dao, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-stage-id" })

    @Test
    fun observe_stages_maps_stored_rows_and_hides_pending_deletes() = runTest {
        val dao = FakeStageDao(
            listOf(
                localStage("a", projectLocalId = "p1", name = "Alpha", estimatedBudget = 100.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localStage("b", projectLocalId = "p1", name = "Beta", pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
                localStage("c", projectLocalId = "other", name = "Gamma", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
            ),
        )

        val stages = repo(dao).observeStages("p1").first()

        assertEquals(listOf("Alpha"), stages.map { it.name }, "other projects and pending deletes are hidden")
        assertEquals(100.0, stages.single().estimatedBudget)
        assertEquals(StageStatus.IN_PROGRESS, stages.single().status)
    }

    @Test
    fun observe_stage_maps_a_single_row_to_the_detail_model() = runTest {
        val dao = FakeStageDao(listOf(localStage("a", name = "Gros œuvre", startDate = "2026-01-01")))

        val detail = repo(dao).observeStage("a").first()

        assertEquals("Gros œuvre", detail?.name)
        assertEquals("2026-01-01", detail?.startDate)
        assertNull(repo(FakeStageDao()).observeStage("missing").first())
    }

    @Test
    fun create_stage_writes_a_pending_create_row_and_nudges_the_syncer() = runTest {
        val dao = FakeStageDao()
        val syncer = FakeSyncer()

        val localId = repo(dao, syncer, MutableClock(4_242L)).createStage(
            CreateStageInput(
                projectLocalId = "p1",
                name = "Fondations",
                description = "  ",
                estimatedBudget = 5000.0,
                startDate = "  ",
                endDate = null,
            ),
        )

        assertEquals("fixed-stage-id", localId)
        val row = dao.findByLocalId(localId)!!
        assertEquals("Fondations", row.name)
        assertEquals("p1", row.projectLocalId)
        assertNull(row.description, "blank optional fields are stored as null")
        assertNull(row.startDate)
        assertEquals(5000.0, row.estimatedBudget)
        assertEquals("IN_PROGRESS", row.status)
        assertNull(row.serverId)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.CREATE, row.pendingOp)
        assertEquals(4_242L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    private fun update(name: String = "Étape rénovée") = UpdateStageInput(
        name = name,
        description = "  ",
        estimatedBudget = null,
        startDate = "2026-03-01",
        endDate = "  ",
        status = StageStatus.COMPLETED,
    )

    @Test
    fun update_stage_writes_a_pending_update_row_and_nudges_the_syncer() = runTest {
        val dao = FakeStageDao(
            listOf(localStage("s1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)),
        )
        val syncer = FakeSyncer()

        repo(dao, syncer, MutableClock(5_555L)).updateStage("s1", update(name = "Étape rénovée"))

        val row = dao.findByLocalId("s1")!!
        assertEquals("Étape rénovée", row.name)
        assertNull(row.description)
        assertEquals("2026-03-01", row.startDate)
        assertNull(row.endDate)
        assertEquals("COMPLETED", row.status)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.UPDATE, row.pendingOp)
        assertEquals(9L, row.serverId)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun update_stage_on_a_row_that_never_synced_keeps_it_a_create() = runTest {
        val dao = FakeStageDao(listOf(localStage("s1", serverId = null, pendingOp = PendingOp.CREATE)))

        repo(dao).updateStage("s1", update())

        assertEquals(PendingOp.CREATE, dao.findByLocalId("s1")!!.pendingOp)
    }

    @Test
    fun update_stage_ignores_an_unknown_local_id() = runTest {
        val dao = FakeStageDao()
        val syncer = FakeSyncer()

        repo(dao, syncer).updateStage("missing", update())

        assertNull(dao.findByLocalId("missing"))
        assertEquals(0, syncer.requestCount)
    }

    @Test
    fun delete_stage_that_reached_the_server_is_marked_pending_delete() = runTest {
        val dao = FakeStageDao(
            listOf(localStage("s1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)),
        )
        val syncer = FakeSyncer()

        repo(dao, syncer, MutableClock(6_000L)).deleteStage("s1")

        val row = dao.findByLocalId("s1")!!
        assertEquals(PendingOp.DELETE, row.pendingOp)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun delete_stage_that_never_reached_the_server_is_dropped_immediately() = runTest {
        val dao = FakeStageDao(listOf(localStage("s1", serverId = null, pendingOp = PendingOp.CREATE)))
        val syncer = FakeSyncer()

        repo(dao, syncer).deleteStage("s1")

        assertNull(dao.findByLocalId("s1"))
        assertEquals(0, syncer.requestCount, "nothing to sync — it only ever existed locally")
    }

    @Test
    fun refresh_stages_delegates_to_a_project_sync() = runTest {
        val syncer = FakeSyncer()
        repo(syncer = syncer).refreshStages("p1")
        assertEquals(listOf("p1"), syncer.syncedProjects)
    }

    @Test
    fun refresh_stage_delegates_to_a_single_stage_sync() = runTest {
        val syncer = FakeSyncer()
        repo(syncer = syncer).refreshStage("s1")
        assertEquals(listOf("s1"), syncer.syncedStages)
    }
}
