package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.support.FakeMaterialRepository
import com.dmb.chantiertracker.support.FakePurchaseLineRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseLineFormViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val ciment = Material("m1", "p1", "Ciment", "sac")

    private fun vm(
        materials: FakeMaterialRepository = FakeMaterialRepository(materials = listOf(ciment)),
        lines: FakePurchaseLineRepository = FakePurchaseLineRepository(),
    ) = Triple(PurchaseLineFormViewModel(materials, lines), materials, lines)

    @Test
    fun creating_a_line_requires_a_material_a_positive_quantity_and_a_non_negative_price() = runTest {
        val (v, _, lines) = vm()
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        v.submit()
        assertTrue(lines.log.isEmpty(), "nothing selected or filled")

        v.selectMaterial(ciment)
        v.onQuantityChange("0")
        v.onUnitPriceChange("3")
        v.submit()
        assertTrue(lines.log.isEmpty(), "quantity must be strictly positive")

        v.onQuantityChange("10")
        v.submit()
        advanceUntilIdle()
        assertEquals("createLine:e1:m1:10.0:3.0:null", lines.log.single())
        assertTrue(v.state.value.saved)
    }

    @Test
    fun a_comma_decimal_and_a_supplier_are_accepted() = runTest {
        val (v, _, lines) = vm()
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        v.selectMaterial(ciment)
        v.onQuantityChange("12,5")
        v.onUnitPriceChange("3,5")
        v.onSupplierChange("  Quincaillerie  ")
        v.submit()
        advanceUntilIdle()

        assertEquals("createLine:e1:m1:12.5:3.5:Quincaillerie", lines.log.single())
    }

    @Test
    fun creating_a_new_material_inline_selects_it() = runTest {
        val materials = FakeMaterialRepository()
        val (v, _, lines) = vm(materials = materials)
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        v.onMaterialQueryChange("Fer")
        assertTrue(v.state.value.canOfferCreate)
        v.startCreateMaterial()
        v.onNewUnitChange("barre")
        v.confirmCreateMaterial()
        advanceUntilIdle()

        assertEquals("createMaterial:p1:Fer:barre", materials.log.single())
        assertEquals("material-new", v.state.value.selectedMaterialId)

        v.onQuantityChange("5")
        v.onUnitPriceChange("20")
        v.submit()
        advanceUntilIdle()
        assertEquals("createLine:e1:material-new:5.0:20.0:null", lines.log.single())
    }

    @Test
    fun editing_a_line_prefills_and_only_touches_quantity_price_supplier() = runTest {
        val lines = FakePurchaseLineRepository(lines = listOf(PurchaseLine("pl1", "e1", "m1", 12.0, 3.5, 42.0, "Quinc")))
        val (v, _, _) = vm(lines = lines)
        v.load("e1", "p1", lineLocalId = "pl1")
        advanceUntilIdle()

        assertEquals("Ciment", v.state.value.materialQuery)
        assertEquals("12", v.state.value.quantity)
        assertEquals("Quinc", v.state.value.supplier)

        v.onQuantityChange("15")
        v.submit()
        advanceUntilIdle()
        assertEquals("updateLine:pl1:15.0:3.5:Quinc", lines.log.single())
    }

    @Test
    fun can_save_stays_false_until_material_quantity_and_price_are_all_present() = runTest {
        val (v, _, _) = vm()
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        assertFalse(v.state.value.canSave)
        v.selectMaterial(ciment)
        assertFalse(v.state.value.canSave)
        v.onQuantityChange("10")
        assertFalse(v.state.value.canSave)
        v.onUnitPriceChange("3")
        assertTrue(v.state.value.canSave)
    }

    @Test
    fun a_missing_line_being_edited_is_flagged() = runTest {
        val (v, _, _) = vm()
        v.load("e1", "p1", lineLocalId = "ghost")
        advanceUntilIdle()
        assertTrue(v.state.value.isMissing)
        assertFalse(v.state.value.ready)
    }
}
