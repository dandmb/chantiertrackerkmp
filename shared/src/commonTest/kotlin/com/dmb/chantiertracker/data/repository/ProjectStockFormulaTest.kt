package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.MaterialStockEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StockMovementRow
import com.dmb.chantiertracker.data.local.db.StockSnapshotEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.support.localMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectStockFormulaTest {

    private val cement = localMaterial("m1", name = "Ciment", unit = "sac", projectLocalId = "p1", serverId = 7)
    private val counter = MaterialStockEntity("p1", 7, quantityIn = 12.0, quantityOut = 2.0)
    private val snapshot = StockSnapshotEntity("p1", refreshedAt = 9_000L, needsRefresh = false)

    private fun movement(
        quantity: Double,
        serverQuantity: Double? = null,
        syncStatus: SyncStatus = SyncStatus.PENDING,
        pendingOp: PendingOp = PendingOp.CREATE,
        parentDeleting: Boolean = false,
    ) = StockMovementRow("m1", quantity, serverQuantity, syncStatus, pendingOp, parentDeleting)

    private fun available(purchases: List<StockMovementRow> = emptyList(), consumptions: List<StockMovementRow> = emptyList()) =
        projectStock(listOf(cement), listOf(counter), snapshot, purchases, consumptions).materials.single().available

    @Test
    fun a_pending_purchase_adds_its_quantity_to_the_server_counter() =
        assertEquals(13.0, available(purchases = listOf(movement(3.0))))

    @Test
    fun a_pending_edit_adds_only_its_difference_with_what_the_server_holds() =
        assertEquals(3.0, available(purchases = listOf(movement(3.0, serverQuantity = 10.0, pendingOp = PendingOp.UPDATE))))

    @Test
    fun a_pending_delete_removes_what_the_server_holds() =
        assertEquals(0.0, available(purchases = listOf(movement(4.0, serverQuantity = 10.0, pendingOp = PendingOp.DELETE))))

    @Test
    fun a_pending_consumption_lowers_the_available_stock() =
        assertEquals(6.0, available(consumptions = listOf(movement(4.0))))

    @Test
    fun a_line_the_server_refused_or_deleted_does_not_count() {
        assertEquals(10.0, available(purchases = listOf(movement(5.0, syncStatus = SyncStatus.CONFLICTED))))
        assertEquals(10.0, available(consumptions = listOf(movement(99.0, syncStatus = SyncStatus.CONFLICTED))))
    }

    @Test
    fun the_lines_of_a_stage_being_deleted_give_back_what_the_server_holds() {
        assertEquals(4.0, available(purchases = listOf(movement(6.0, serverQuantity = 6.0, syncStatus = SyncStatus.SYNCED, pendingOp = PendingOp.NONE, parentDeleting = true))))
        assertEquals(10.0, available(purchases = listOf(movement(6.0, parentDeleting = true))), "never sent: nothing to give back")
    }

    @Test
    fun a_material_not_yet_on_the_server_starts_from_zero() {
        val local = localMaterial("m2", name = "Sable", projectLocalId = "p1", serverId = null)
        val stock = projectStock(listOf(local), listOf(counter), snapshot, listOf(StockMovementRow("m2", 2.5, null, SyncStatus.PENDING, PendingOp.CREATE, false)), emptyList())

        assertEquals(2.5, stock.materials.single().available)
    }

    @Test
    fun without_a_snapshot_the_stock_is_not_loaded() =
        assertNull(projectStock(listOf(cement), emptyList(), null, emptyList(), emptyList()).refreshedAt)
}
