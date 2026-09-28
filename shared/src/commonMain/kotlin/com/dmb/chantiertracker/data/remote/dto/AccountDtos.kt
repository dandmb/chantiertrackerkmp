package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlanUsageDto(
    val plan: String,
    val projectsUsed: Long = 0,
    val projectsLimit: Int? = null,
    val photosUsed: Long = 0,
    val photosLimit: Int? = null,
    val videosUsed: Long = 0,
    val videosLimit: Int = 0,
    val videoDurationLimitSeconds: Int = 0,
    val supervisorsUsed: Long = 0,
    val supervisorsLimit: Int? = null,
    val planExpiresAt: String? = null,
    val hasStripeCustomer: Boolean = false,
)
