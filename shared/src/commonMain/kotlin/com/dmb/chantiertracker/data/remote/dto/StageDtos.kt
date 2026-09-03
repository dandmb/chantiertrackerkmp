package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StageDto(
    val id: Long,
    val projectId: Long,
    val name: String,
    val description: String? = null,
    val estimatedBudget: Double? = null,
    val spentAmount: Double? = null,
    val spentPercentage: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String,
    val createdAt: String? = null,
)

@Serializable
data class CreateStageRequestDto(
    val name: String,
    val description: String? = null,
    val estimatedBudget: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)

@Serializable
data class UpdateStageRequestDto(
    val name: String? = null,
    val description: String? = null,
    val estimatedBudget: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String? = null,
)
