package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlanUsageDto(
    val plan: String,
    val projectsLimit: Int? = null,
)
