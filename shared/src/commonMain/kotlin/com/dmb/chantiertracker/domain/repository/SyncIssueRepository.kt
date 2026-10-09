package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.SyncIssueItem
import kotlinx.coroutines.flow.Flow

enum class RetryOutcome { ACCEPTED, STILL_REFUSED, NOT_SENT }

enum class RevertOutcome { RESTORED, NEEDS_CONNECTION, FAILED }

interface SyncIssueRepository {
    fun observeIssues(): Flow<List<SyncIssueItem>>

    fun observeIssueCount(): Flow<Int>

    suspend fun retry(item: SyncIssueItem): RetryOutcome

    fun observeOnline(): Flow<Boolean>

    suspend fun linkedCount(item: SyncIssueItem): Int

    suspend fun discard(item: SyncIssueItem)

    suspend fun acknowledge(item: SyncIssueItem)

    suspend fun revert(item: SyncIssueItem): RevertOutcome
}
