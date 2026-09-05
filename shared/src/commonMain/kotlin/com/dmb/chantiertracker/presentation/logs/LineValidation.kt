package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_material_required
import com.dmb.chantiertracker.resources.validation_quantity_positive
import com.dmb.chantiertracker.resources.validation_quantity_required
import com.dmb.chantiertracker.resources.validation_unit_price_not_negative
import com.dmb.chantiertracker.resources.validation_unit_price_required
import org.jetbrains.compose.resources.StringResource

fun validateMaterialSelected(materialLocalId: String?): StringResource? =
    if (materialLocalId.isNullOrBlank()) Res.string.validation_material_required else null

fun validateRequiredQuantity(value: String): StringResource? = when {
    value.trim().isEmpty() -> Res.string.validation_quantity_required
    (parseAmountOrNull(value) ?: -1.0) > 0.0 -> null
    else -> Res.string.validation_quantity_positive
}

fun validateRequiredUnitPrice(value: String): StringResource? = when {
    value.trim().isEmpty() -> Res.string.validation_unit_price_required
    (parseAmountOrNull(value) ?: -1.0) >= 0.0 -> null
    else -> Res.string.validation_unit_price_not_negative
}

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
