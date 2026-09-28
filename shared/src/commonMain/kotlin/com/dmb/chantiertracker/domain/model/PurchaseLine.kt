package com.dmb.chantiertracker.domain.model

data class PurchaseLine(
    val localId: String,
    val entryLocalId: String,
    val materialLocalId: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val supplier: String?,
)

data class CreatePurchaseLineInput(
    val materialLocalId: String,
    val quantity: Double,
    val unitPrice: Double,
    val supplier: String?,
)

data class UpdatePurchaseLineInput(
    val quantity: Double,
    val unitPrice: Double,
    val supplier: String?,
)

data class ConsumptionLine(
    val localId: String,
    val entryLocalId: String,
    val materialLocalId: String,
    val quantity: Double,
)

data class CreateConsumptionLineInput(
    val materialLocalId: String,
    val quantity: Double,
)

data class UpdateConsumptionLineInput(
    val quantity: Double,
)
