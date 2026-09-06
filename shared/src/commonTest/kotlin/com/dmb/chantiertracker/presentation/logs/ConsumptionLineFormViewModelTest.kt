package com.dmb.chantiertracker.presentation.logs

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
    fun a_missing_line_being_edited_is_flagged() = runTest {
        val (v, _, _) = vm(stock = emptyList())
        v.load("e1", "p1", lineLocalId = "ghost")
        advanceUntilIdle()
        assertTrue(v.state.value.isMissing)
    }
}
