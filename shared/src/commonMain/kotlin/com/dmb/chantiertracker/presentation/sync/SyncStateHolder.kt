package com.dmb.chantiertracker.presentation.sync

import com.dmb.chantiertracker.domain.model.DomainException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Offline : SyncState
    data class Error(val cause: DomainException) : SyncState
}

/**
 * Session-scoped, observed by the UI to show a discreet, non-blocking sync
 * indicator. Same singleton pattern as [com.dmb.chantiertracker.presentation.projects.ProjectSortHolder].
 */
class SyncStateHolder {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun update(state: SyncState) {
        _state.value = state
    }
}
