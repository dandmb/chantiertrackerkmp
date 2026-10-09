package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_amount_two_decimals
import com.dmb.chantiertracker.resources.validation_material_required
import com.dmb.chantiertracker.resources.validation_quantity_positive
import com.dmb.chantiertracker.resources.validation_quantity_required
import com.dmb.chantiertracker.resources.validation_quantity_too_large
import com.dmb.chantiertracker.resources.validation_unit_price_not_negative
import com.dmb.chantiertracker.resources.validation_unit_price_required
import com.dmb.chantiertracker.resources.validation_unit_price_too_large
import org.jetbrains.compose.resources.StringResource

fun validateMaterialSelected(materialLocalId: String?): StringResource? =
    if (materialLocalId.isNullOrBlank()) Res.string.validation_material_required else null

private const val MAX_DECIMALS = 2
private const val QUANTITY_UPPER_BOUND = 10_000_000_000.0
private const val UNIT_PRICE_UPPER_BOUND = 10_000_000_000_000.0

fun validateRequiredQuantity(value: String): StringResource? {
    if (value.trim().isEmpty()) return Res.string.validation_quantity_required
    val amount = parseAmountOrNull(value) ?: -1.0
    return when {
        amount <= 0.0 -> Res.string.validation_quantity_positive
        decimalsOf(value) > MAX_DECIMALS -> Res.string.validation_amount_two_decimals
        amount >= QUANTITY_UPPER_BOUND -> Res.string.validation_quantity_too_large
        else -> null
    }
}

fun validateRequiredUnitPrice(value: String): StringResource? {
    if (value.trim().isEmpty()) return Res.string.validation_unit_price_required
    val amount = parseAmountOrNull(value) ?: -1.0
    return when {
        amount < 0.0 -> Res.string.validation_unit_price_not_negative
        decimalsOf(value) > MAX_DECIMALS -> Res.string.validation_amount_two_decimals
        amount >= UNIT_PRICE_UPPER_BOUND -> Res.string.validation_unit_price_too_large
        else -> null
    }
}

private fun decimalsOf(value: String): Int =
    value.trim().replace(',', '.').substringAfter('.', "").trimEnd('0').length

fun parseAmountOrNull(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

/**
 * The highest quantity a consumption line can be saved with, mirroring the
 * web's `ceiling` (`ConsumptionLineFormDialog`): the material's currently
 * available stock, plus the line's own quantity if it's being edited (that
 * quantity is already counted as "consumed" in [stock] until the edit is
 * saved, so it must be given back before checking the new value against it).
 */
fun availableCeiling(stock: List<MaterialStock>, materialLocalId: String, editingLineQuantity: Double?): Double {
    val available = stock.firstOrNull { it.materialLocalId == materialLocalId }?.available ?: 0.0
    return available + (editingLineQuantity ?: 0.0)
}
