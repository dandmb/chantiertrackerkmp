package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.support.FakeConsumptionLineDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localConsumptionLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsumptionLineRepositoryImplTest {

    private fun repo(
        dao: FakeConsumptionLineDao = FakeConsumptionLineDao(),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = ConsumptionLineRepositoryImpl(dao, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-line-id" })

    @Test
    fun observe_lines_maps_stored_rows_and_hides_pending_deletes() = runTest {
        val dao = FakeConsumptionLineDao(
            listOf(
                localConsumptionLine("a", entryLocalId = "e1", quantity = 4.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localConsumptionLine("b", entryLocalId = "e1", pendingOp = PendingOp.DELETE),
                localConsumptionLine("c", entryLocalId = "other-entry"),
            ),
        )

        val lines = repo(dao).observeLines("e1").first()

        assertEquals(listOf("a"), lines.map { it.localId })
        assertEquals(4.0, lines.single().quantity)
    }

    @Test
    fun create_line_writes_a_pending_create_row_and_nudges_the_syncer() = runTest {
        val dao = FakeConsumptionLineDao()
        val syncer = FakeSyncer()

        val localId = repo(dao, syncer, MutableClock(4_242L)).createLine("e1", CreateConsumptionLineInput(materialLocalId = "m1", quantity = 4.0))

        assertEquals("fixed-line-id", localId)
        val row = dao.findByLocalId(localId)!!
        assertEquals("e1", row.entryLocalId)
        assertEquals("m1", row.materialLocalId)
        assertEquals(4.0, row.quantity)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.CREATE, row.pendingOp)
        assertEquals(4_242L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun update_line_writes_a_pending_update_and_preserves_the_server_id() = runTest {
        val dao = FakeConsumptionLineDao(listOf(localConsumptionLine("l1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)))
        val syncer = FakeSyncer()

        repo(dao, syncer, MutableClock(5_555L)).updateLine("l1", UpdateConsumptionLineInput(quantity = 6.0))

        val row = dao.findByLocalId("l1")!!
        assertEquals(6.0, row.quantity)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.UPDATE, row.pendingOp)
        assertEquals(9L, row.serverId)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun update_line_on_a_row_that_never_synced_keeps_it_a_create() = runTest {
        val dao = FakeConsumptionLineDao(listOf(localConsumptionLine("l1", serverId = null, pendingOp = PendingOp.CREATE)))

        repo(dao).updateLine("l1", UpdateConsumptionLineInput(2.0))

        assertEquals(PendingOp.CREATE, dao.findByLocalId("l1")!!.pendingOp)
    }

    @Test
    fun delete_line_that_reached_the_server_is_marked_pending_delete() = runTest {
        val dao = FakeConsumptionLineDao(listOf(localConsumptionLine("l1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)))
        val syncer = FakeSyncer()

        repo(dao, syncer).deleteLine("l1")

        val row = dao.findByLocalId("l1")!!
        assertEquals(PendingOp.DELETE, row.pendingOp)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun delete_line_that_never_reached_the_server_is_dropped_immediately() = runTest {
        val dao = FakeConsumptionLineDao(listOf(localConsumptionLine("l1", serverId = null, pendingOp = PendingOp.CREATE)))
        val syncer = FakeSyncer()

        repo(dao, syncer).deleteLine("l1")

        assertNull(dao.findByLocalId("l1"))
        assertEquals(0, syncer.requestCount)
    }
}
