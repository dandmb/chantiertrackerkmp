package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.sync.AppCoroutineScope
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
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = MaterialRepositoryImpl(materialDao, purchaseLineDao, consumptionLineDao, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-material-id" })

    @Test
    fun observe_materials_maps_stored_rows_sorted_by_name() = runTest {
        val dao = FakeMaterialDao(listOf(localMaterial("m2", name = "Fer", projectLocalId = "proj-1"), localMaterial("m1", name = "Ciment", projectLocalId = "proj-1")))

        val materials = repo(dao).observeMaterials("proj-1").first()

        assertEquals(listOf("Ciment", "Fer"), materials.map { it.name })
    }

    @Test
    fun observe_stock_computes_available_from_purchase_and_consumption_lines() = runTest {
        val materialDao = FakeMaterialDao(listOf(localMaterial("m1", name = "Ciment", unit = "sac", projectLocalId = "proj-1")))
        val purchaseDao = FakePurchaseLineDao(
            listOf(
                localPurchaseLine("pl1", entryLocalId = "e1", materialLocalId = "m1", quantity = 100.0),
                localPurchaseLine("pl2", entryLocalId = "e1", materialLocalId = "m1", quantity = 50.0),
            ),
            projectForEntry = entryToProject,
        )
        val consumptionDao = FakeConsumptionLineDao(
            listOf(localConsumptionLine("cl1", entryLocalId = "e2", materialLocalId = "m1", quantity = 30.0)),
            projectForEntry = entryToProject,
        )

        val stock = repo(materialDao, purchaseDao, consumptionDao).observeStock("proj-1").first()

        val ciment = stock.single()
        assertEquals(150.0, ciment.quantityIn)
        assertEquals(30.0, ciment.quantityOut)
        assertEquals(120.0, ciment.available)
    }

    @Test
    fun observe_stock_excludes_lines_from_other_projects() = runTest {
        val materialDao = FakeMaterialDao(listOf(localMaterial("m1", name = "Ciment", projectLocalId = "proj-1")))
        val purchaseDao = FakePurchaseLineDao(
            listOf(localPurchaseLine("other", entryLocalId = "other-entry", materialLocalId = "m1", quantity = 999.0)),
            projectForEntry = entryToProject,
        )

        val stock = repo(materialDao, purchaseDao).observeStock("proj-1").first()

        assertEquals(0.0, stock.single().quantityIn, "a line logged under another project must not count here")
    }

    @Test
    fun observe_stock_ignores_pending_delete_lines() = runTest {
        val materialDao = FakeMaterialDao(listOf(localMaterial("m1", name = "Ciment", projectLocalId = "proj-1")))
        val purchaseDao = FakePurchaseLineDao(
            listOf(localPurchaseLine("pl1", entryLocalId = "e1", materialLocalId = "m1", quantity = 100.0, pendingOp = com.dmb.chantiertracker.data.local.db.PendingOp.DELETE)),
            projectForEntry = entryToProject,
        )

        val stock = repo(materialDao, purchaseDao).observeStock("proj-1").first()

        assertEquals(0.0, stock.single().quantityIn)
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
