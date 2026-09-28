package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.support.FakePurchaseLineDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localPurchaseLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PurchaseLineRepositoryImplTest {

    private fun repo(
        dao: FakePurchaseLineDao = FakePurchaseLineDao(),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = PurchaseLineRepositoryImpl(dao, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-line-id" })

    @Test
    fun observe_lines_maps_stored_rows_and_hides_pending_deletes() = runTest {
        val dao = FakePurchaseLineDao(
            listOf(
                localPurchaseLine("a", entryLocalId = "e1", quantity = 2.0, unitPrice = 5.0, totalPrice = 10.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localPurchaseLine("b", entryLocalId = "e1", pendingOp = PendingOp.DELETE),
                localPurchaseLine("c", entryLocalId = "other-entry"),
            ),
        )

        val lines = repo(dao).observeLines("e1").first()

        assertEquals(listOf("a"), lines.map { it.localId }, "other entries and pending deletes are hidden")
        assertEquals(10.0, lines.single().totalPrice)
    }

    @Test
    fun create_line_writes_a_pending_create_row_with_computed_total_and_nudges_the_syncer() = runTest {
        val dao = FakePurchaseLineDao()
        val syncer = FakeSyncer()

        val localId = repo(dao, syncer, MutableClock(4_242L)).createLine(
            "e1",
            CreatePurchaseLineInput(materialLocalId = "m1", quantity = 12.0, unitPrice = 3.5, supplier = "  Quincaillerie  "),
        )

        assertEquals("fixed-line-id", localId)
        val row = dao.findByLocalId(localId)!!
        assertEquals("e1", row.entryLocalId)
        assertEquals("m1", row.materialLocalId)
        assertEquals(12.0, row.quantity)
        assertEquals(3.5, row.unitPrice)
        assertEquals(42.0, row.totalPrice, "total is computed, not passed in")
        assertEquals("  Quincaillerie  ", row.supplier, "supplier is only trimmed to null when blank, not otherwise altered")
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.CREATE, row.pendingOp)
        assertEquals(4_242L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun create_line_with_a_blank_supplier_stores_null() = runTest {
        val dao = FakePurchaseLineDao()

        val localId = repo(dao).createLine("e1", CreatePurchaseLineInput("m1", 1.0, 1.0, supplier = "   "))

        assertNull(dao.findByLocalId(localId)!!.supplier)
    }

    @Test
    fun update_line_recomputes_the_total_and_writes_a_pending_update() = runTest {
        val dao = FakePurchaseLineDao(listOf(localPurchaseLine("l1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)))
        val syncer = FakeSyncer()

        repo(dao, syncer, MutableClock(5_555L)).updateLine("l1", UpdatePurchaseLineInput(quantity = 10.0, unitPrice = 2.0, supplier = null))

        val row = dao.findByLocalId("l1")!!
        assertEquals(10.0, row.quantity)
        assertEquals(2.0, row.unitPrice)
        assertEquals(20.0, row.totalPrice)
        assertNull(row.supplier)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.UPDATE, row.pendingOp)
        assertEquals(9L, row.serverId, "the server id is preserved")
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun update_line_on_a_row_that_never_synced_keeps_it_a_create() = runTest {
        val dao = FakePurchaseLineDao(listOf(localPurchaseLine("l1", serverId = null, pendingOp = PendingOp.CREATE)))

        repo(dao).updateLine("l1", UpdatePurchaseLineInput(1.0, 1.0, null))

        assertEquals(PendingOp.CREATE, dao.findByLocalId("l1")!!.pendingOp)
    }

    @Test
    fun update_line_ignores_an_unknown_local_id() = runTest {
        val dao = FakePurchaseLineDao()
        val syncer = FakeSyncer()

        repo(dao, syncer).updateLine("missing", UpdatePurchaseLineInput(1.0, 1.0, null))

        assertEquals(0, syncer.requestCount)
    }

    @Test
    fun delete_line_that_reached_the_server_is_marked_pending_delete() = runTest {
        val dao = FakePurchaseLineDao(listOf(localPurchaseLine("l1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)))
        val syncer = FakeSyncer()

        repo(dao, syncer).deleteLine("l1")

        val row = dao.findByLocalId("l1")!!
        assertEquals(PendingOp.DELETE, row.pendingOp)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun delete_line_that_never_reached_the_server_is_dropped_immediately() = runTest {
        val dao = FakePurchaseLineDao(listOf(localPurchaseLine("l1", serverId = null, pendingOp = PendingOp.CREATE)))
        val syncer = FakeSyncer()

        repo(dao, syncer).deleteLine("l1")

        assertNull(dao.findByLocalId("l1"))
        assertEquals(0, syncer.requestCount, "nothing to sync — it only ever existed locally")
    }
}
