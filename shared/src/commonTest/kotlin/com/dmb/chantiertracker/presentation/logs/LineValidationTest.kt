package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_material_required
import com.dmb.chantiertracker.resources.validation_quantity_positive
import com.dmb.chantiertracker.resources.validation_quantity_required
import com.dmb.chantiertracker.resources.validation_unit_price_not_negative
import com.dmb.chantiertracker.resources.validation_unit_price_required
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LineValidationTest {

    @Test
    fun material_selection_is_required() {
        assertEquals(Res.string.validation_material_required, validateMaterialSelected(null))
        assertEquals(Res.string.validation_material_required, validateMaterialSelected(""))
        assertNull(validateMaterialSelected("m1"))
    }

    @Test
    fun quantity_must_be_present_and_strictly_positive() {
        assertEquals(Res.string.validation_quantity_required, validateRequiredQuantity(""))
        assertEquals(Res.string.validation_quantity_required, validateRequiredQuantity("   "))
        assertEquals(Res.string.validation_quantity_positive, validateRequiredQuantity("0"))
        assertEquals(Res.string.validation_quantity_positive, validateRequiredQuantity("-1"))
        assertEquals(Res.string.validation_quantity_positive, validateRequiredQuantity("abc"))
        assertNull(validateRequiredQuantity("12"))
        assertNull(validateRequiredQuantity("2,5"), "a comma decimal separator is accepted")
    }

    @Test
    fun unit_price_must_be_present_and_not_negative() {
        assertEquals(Res.string.validation_unit_price_required, validateRequiredUnitPrice(""))
        assertEquals(Res.string.validation_unit_price_not_negative, validateRequiredUnitPrice("-1"))
        assertNull(validateRequiredUnitPrice("0"), "a free item (price 0) is allowed, unlike quantity")
        assertNull(validateRequiredUnitPrice("3.5"))
    }

    @Test
    fun parse_amount_accepts_a_comma_or_dot_decimal_separator() {
        assertEquals(12.5, parseAmountOrNull("12,5"))
        assertEquals(12.5, parseAmountOrNull("12.5"))
        assertNull(parseAmountOrNull("abc"))
    }

    private val stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 100.0, quantityOut = 40.0))

    @Test
    fun ceiling_for_a_new_line_is_the_materials_available_stock() {
        assertEquals(60.0, availableCeiling(stock, "m1", editingLineQuantity = null))
    }

    @Test
    fun ceiling_for_an_edited_line_gives_back_its_own_quantity_first() {
        // The line's own 10 units are already counted in quantityOut (40) until
        // the edit is saved — the ceiling must include them back.
        assertEquals(70.0, availableCeiling(stock, "m1", editingLineQuantity = 10.0))
    }

    @Test
    fun ceiling_for_an_unknown_material_is_zero() {
        assertEquals(0.0, availableCeiling(stock, "unknown", editingLineQuantity = null))
    }
}
