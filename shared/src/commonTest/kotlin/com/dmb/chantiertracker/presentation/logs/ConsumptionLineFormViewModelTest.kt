package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_amount_two_decimals
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.support.FakeConsumptionLineRepository
import com.dmb.chantiertracker.support.FakeMaterialRepository
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
class ConsumptionLineFormViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun vm(
        stock: List<MaterialStock>,
        lines: FakeConsumptionLineRepository = FakeConsumptionLineRepository(),
    ): Triple<ConsumptionLineFormViewModel, FakeMaterialRepository, FakeConsumptionLineRepository> {
        val materials = FakeMaterialRepository(stock = stock)
        return Triple(ConsumptionLineFormViewModel(materials, lines), materials, lines)
    }

    @Test
    fun the_picker_only_offers_materials_with_stock_left() = runTest {
        val (v, _, _) = vm(
            stock = listOf(
                MaterialStock("m1", "Ciment", "sac", quantityIn = 10.0, quantityOut = 4.0),
                MaterialStock("m2", "Fer", "barre", quantityIn = 3.0, quantityOut = 3.0),
            ),
        )
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        assertEquals(listOf("m1"), v.state.value.pickable.map { it.materialLocalId })
    }

    @Test
    fun a_quantity_within_the_ceiling_is_saved_and_beyond_it_is_blocked() = runTest {
        val (v, _, lines) = vm(stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 10.0, quantityOut = 0.0)))
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        v.selectMaterial("m1")
        v.onQuantityChange("11")
        assertTrue(v.state.value.exceedsStock)
        v.submit()
        assertTrue(lines.log.isEmpty())

        v.onQuantityChange("4")
        v.submit()
        advanceUntilIdle()
        assertEquals("createLine:e1:m1:4.0", lines.log.single())
    }

    @Test
    fun editing_a_line_gives_its_own_quantity_back_before_checking_the_ceiling() = runTest {
        // 10 still available; this line already consumed 5 (counted in quantityOut).
        val lines = FakeConsumptionLineRepository(lines = listOf(ConsumptionLine("cl1", "e1", "m1", 5.0)))
        val (v, _, _) = vm(
            stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 20.0, quantityOut = 10.0)),
            lines = lines,
        )
        v.load("e1", "p1", lineLocalId = "cl1")
        advanceUntilIdle()

        v.onQuantityChange("15") // ceiling = 10 available + 5 own = 15
        assertFalse(v.state.value.exceedsStock)
        v.submit()
        advanceUntilIdle()
        assertEquals("updateLine:cl1:15.0", lines.log.single())
    }

    @Test
    fun can_save_needs_a_material_a_quantity_and_a_value_within_stock() = runTest {
        val (v, _, _) = vm(stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 10.0, quantityOut = 0.0)))
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        assertFalse(v.state.value.canSave)
        v.selectMaterial("m1")
        assertFalse(v.state.value.canSave)
        v.onQuantityChange("11")
        assertFalse(v.state.value.canSave) // exceeds stock
        v.onQuantityChange("4")
        assertTrue(v.state.value.canSave)
    }

    @Test
    fun a_missing_line_being_edited_is_flagged() = runTest {
        val (v, _, _) = vm(stock = emptyList())
        v.load("e1", "p1", lineLocalId = "ghost")
        advanceUntilIdle()
        assertTrue(v.state.value.isMissing)
    }

    @Test
    fun a_stock_never_loaded_offers_every_material_without_a_ceiling() = runTest {
        val materials = FakeMaterialRepository(
            stock = listOf(
                MaterialStock("m1", "Ciment", "sac", quantityIn = 0.0, quantityOut = 0.0),
                MaterialStock("m2", "Fer", "barre", quantityIn = 0.0, quantityOut = 0.0),
            ),
            stockRefreshedAt = null,
        )
        val lines = FakeConsumptionLineRepository()
        val v = ConsumptionLineFormViewModel(materials, lines)
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        assertFalse(v.state.value.stockIsLoaded)
        assertEquals(listOf("m1", "m2"), v.state.value.pickable.map { it.materialLocalId }, "offline-first: the server will check it when sending")
        v.selectMaterial("m1")
        v.onQuantityChange("40")
        assertEquals(null, v.state.value.ceiling)
        assertFalse(v.state.value.exceedsStock)
        v.submit()
        advanceUntilIdle()
        assertEquals(1, lines.log.size)
    }

    @Test
    fun a_loaded_stock_exposes_when_it_was_loaded() = runTest {
        val materials = FakeMaterialRepository(stock = listOf(MaterialStock("m1", "Ciment", "sac", 12.0, 0.0)), stockRefreshedAt = 9_000L)
        val v = ConsumptionLineFormViewModel(materials, FakeConsumptionLineRepository())
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        assertTrue(v.state.value.stockIsLoaded)
        assertEquals(9_000L, v.state.value.stockRefreshedAt)
        v.selectMaterial("m1")
        assertEquals(12.0, v.state.value.ceiling)
    }

    @Test
    fun a_quantity_with_three_decimals_is_refused_before_anything_is_saved() = runTest {
        val (v, _, lines) = vm(stock = listOf(MaterialStock("m1", "Sable", "t", quantityIn = 10.0, quantityOut = 0.0)))
        v.load("e1", "p1", lineLocalId = null)
        advanceUntilIdle()

        v.selectMaterial("m1")
        v.onQuantityChange("2,675")
        v.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_amount_two_decimals, v.state.value.quantityError)
        assertTrue(lines.log.isEmpty(), "never sent: the server would refuse it (C-4)")
    }
}
