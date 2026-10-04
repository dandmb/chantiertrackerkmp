package com.dmb.chantiertracker.domain.model

data class Material(
    val localId: String,
    val projectLocalId: String,
    val name: String,
    val unit: String,
)

data class ProjectStock(
    val materials: List<MaterialStock>,
    val refreshedAt: Long?,
) {
    val isLoaded: Boolean get() = refreshedAt != null
}

data class MaterialStock(
    val materialLocalId: String,
    val materialName: String,
    val unit: String,
    val quantityIn: Double,
    val quantityOut: Double,
) {
    val available: Double get() = quantityIn - quantityOut
}
