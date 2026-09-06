package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PurchaseLineDto(
    val id: Long,
    val entryId: Long,
    val materialId: Long,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val supplier: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreatePurchaseLineRequestDto(
    val materialId: Long,
    val quantity: Double,
    val unitPrice: Double,
    val supplier: String? = null,
)

@Serializable
data class UpdatePurchaseLineRequestDto(
    val quantity: Double,
    val unitPrice: Double,
    val supplier: String? = null,
)

@Serializable
data class ConsumptionLineDto(
    val id: Long,
    val entryId: Long,
    val materialId: Long,
    val quantity: Double,
    val createdAt: String? = null,
)

@Serializable
data class CreateConsumptionLineRequestDto(
    val materialId: Long,
    val quantity: Double,
)

@Serializable
data class UpdateConsumptionLineRequestDto(
    val quantity: Double,
)
