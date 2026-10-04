package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.MaterialStockEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.support.FakeStockDao
import com.dmb.chantiertracker.support.FakeConsumptionLineDao
import com.dmb.chantiertracker.support.FakeMaterialDao
import com.dmb.chantiertracker.support.FakePurchaseLineDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localPurchaseLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaterialRepositoryImplTest {

    // Both purchase and consumption lines in this project's fixtures live on
    // entries "e1"/"e2" — see [FakePurchaseLineDao]/[FakeConsumptionLineDao] on
    // why a plain map stands in for the real entry→…→project join.
    private val entryToProject = { mapOf("e1" to "proj-1", "e2" to "proj-1", "other-entry" to "proj-2") }

    private fun repo(
        materialDao: FakeMaterialDao = FakeMaterialDao(),
        purchaseLineDao: FakePurchaseLineDao = FakePurchaseLineDao(projectForEntry = entryToProject),
        consumptionLineDao: FakeConsumptionLineDao = FakeConsumptionLineDao(projectForEntry = entryToProject),
        stockDao: FakeStockDao = FakeStockDao(purchaseLineDao, consumptionLineDao),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = MaterialRepositoryImpl(materialDao, purchaseLineDao, consumptionLineDao, stockDao, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-material-id" })

    @Test
    fun observe_materials_maps_stored_rows_sorted_by_name() = runTest {
        val dao = FakeMaterialDao(listOf(localMaterial("m2", name = "Fer", projectLocalId = "proj-1"), localMaterial("m1", name = "Ciment", projectLocalId = "proj-1")))

        val materials = repo(dao).observeMaterials("proj-1").first()

        assertEquals(listOf("Ciment", "Fer"), materials.map { it.name })
    }

    private suspend fun loadedStock(stockDao: FakeStockDao, vararg counters: Pair<Long, Pair<Double, Double>>) {
        stockDao.replaceCounters(
            "proj-1",
            counters.map { (materialServerId, inOut) -> MaterialStockEntity("proj-1", materialServerId, inOut.first, inOut.second) },
            refreshedAt = 5_000L,
        )
    }

    @Test
    fun the_stock_is_the_server_counter_plus_the_pending_lines_of_this_project() = runTest {
        val materialDao = FakeMaterialDao(listOf(localMaterial("m1", name = "Ciment", unit = "sac", projectLocalId = "proj-1", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)))
        val purchaseDao = FakePurchaseLineDao(
            listOf(
                localPurchaseLine("pl-pending", entryLocalId = "e1", materialLocalId = "m1", quantity = 5.0),
                localPurchaseLine("pl-synced", entryLocalId = "e1", materialLocalId = "m1", quantity = 100.0, serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED).copy(serverQuantity = 100.0),
                localPurchaseLine("pl-other-project", entryLocalId = "other-entry", materialLocalId = "m1", quantity = 999.0),
            ),
            projectForEntry = entryToProject,
        )
        val consumptionDao = FakeConsumptionLineDao(
            listOf(localConsumptionLine("cl-pending", entryLocalId = "e2", materialLocalId = "m1", quantity = 3.0)),
            projectForEntry = entryToProject,
        )
        val stockDao = FakeStockDao(purchaseDao, consumptionDao)
        loadedStock(stockDao, 7L to (12.0 to 2.0))

        val stock = repo(materialDao, purchaseDao, consumptionDao, stockDao).observeStock("proj-1").first()

        val ciment = stock.materials.single()
        assertEquals(17.0, ciment.quantityIn, "server 12 + this device's pending 5; the synced line is already in the counter")
        assertEquals(5.0, ciment.quantityOut)
        assertEquals(12.0, ciment.available)
        assertEquals(5_000L, stock.refreshedAt)
    }

    @Test
    fun a_stock_never_loaded_says_so_instead_of_a_false_zero() = runTest {
        val materialDao = FakeMaterialDao(listOf(localMaterial("m1", name = "Ciment", projectLocalId = "proj-1", serverId = 7)))

        val stock = repo(materialDao).observeStock("proj-1").first()

        assertFalse(stock.isLoaded)
        assertEquals(listOf("Ciment"), stock.materials.map { it.materialName }, "every material stays listed")
    }

    @Test
    fun create_material_writes_a_pending_create_row_and_nudges_the_syncer() = runTest {
        val dao = FakeMaterialDao()
        val syncer = FakeSyncer()

        val material = repo(dao, syncer = syncer, clock = MutableClock(4_242L)).createMaterial("proj-1", "Ciment", "sac")

        assertEquals("fixed-material-id", material.localId)
        assertEquals("Ciment", material.name)
        val row = dao.findByLocalId(material.localId)!!
        assertEquals("sac", row.unit)
        assertEquals(com.dmb.chantiertracker.data.local.db.SyncStatus.PENDING, row.syncStatus)
        assertEquals(com.dmb.chantiertracker.data.local.db.PendingOp.CREATE, row.pendingOp)
        assertEquals(4_242L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun creating_a_material_with_an_existing_name_reuses_it_case_insensitively() = runTest {
        val dao = FakeMaterialDao(listOf(localMaterial("m1", name = "Ciment", unit = "sac", projectLocalId = "proj-1")))
        val syncer = FakeSyncer()

        val material = repo(dao, syncer = syncer).createMaterial("proj-1", "ciment", "sac")

        assertEquals("m1", material.localId, "the existing material is reused")
        assertEquals(1, dao.stored.size, "no duplicate row created")
        assertTrue(syncer.requestCount == 0, "reusing an existing material needs no sync nudge")
    }
}
