package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.SyncIssueItem
import kotlinx.coroutines.flow.Flow

enum class RetryOutcome { ACCEPTED, STILL_REFUSED, NOT_SENT }

interface SyncIssueRepository {
    fun observeIssues(): Flow<List<SyncIssueItem>>

    fun observeIssueCount(): Flow<Int>

    suspend fun retry(item: SyncIssueItem): RetryOutcome
}
