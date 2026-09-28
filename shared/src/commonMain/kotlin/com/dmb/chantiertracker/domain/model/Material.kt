package com.dmb.chantiertracker.domain.model

data class Material(
    val localId: String,
    val projectLocalId: String,
    val name: String,
    val unit: String,
)

/**
 * A material's running total for a project — `available` is never persisted,
 * always derived from the project's purchase/consumption lines (same
 * principle as a stage's spent budget: computed, never stored).
 */
data class MaterialStock(
    val materialLocalId: String,
    val materialName: String,
    val unit: String,
    val quantityIn: Double,
    val quantityOut: Double,
) {
    val available: Double get() = quantityIn - quantityOut
}
