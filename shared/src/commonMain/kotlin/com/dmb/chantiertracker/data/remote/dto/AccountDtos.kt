package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

// historyDaysLimit = null is a real answer ("unlimited"). This default only
// applies when the key is missing altogether (older backend), which a plain
// nullable could not tell apart.
const val HISTORY_DAYS_LIMIT_NOT_SENT = -1

@Serializable
data class PlanUsageDto(
    val plan: String,
    val isFounder: Boolean = false,
    val projectsUsed: Long = 0,
    val projectsLimit: Int? = null,
    val photosUsed: Long = 0,
    val photosLimit: Int? = null,
    val videosUsed: Long = 0,
    val videosLimit: Int = 0,
    val videoDurationLimitSeconds: Int = 0,
    val supervisorsUsed: Long = 0,
    val supervisorsLimit: Int? = null,
    val historyDaysLimit: Int? = HISTORY_DAYS_LIMIT_NOT_SENT,
    val planExpiresAt: String? = null,
    val hasStripeCustomer: Boolean = false,
)
