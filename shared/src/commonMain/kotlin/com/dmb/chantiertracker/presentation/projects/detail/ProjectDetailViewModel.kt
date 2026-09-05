package com.dmb.chantiertracker.presentation.projects.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.domain.model.projectAdmin
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val isLoading: Boolean = true,
    val detail: ProjectDetail? = null,
    val canEdit: Boolean = false,
    val stages: List<Stage> = emptyList(),
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
) {
    val isMissing: Boolean get() = !isLoading && detail == null && !deleted
}

class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val stageRepository: StageRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectDetailUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null

    fun load(projectLocalId: String) {
        if (localId == projectLocalId) return
        localId = projectLocalId

        viewModelScope.launch {
            combine(
                projectRepository.observeProject(projectLocalId),
                projectRepository.observeMembers(projectLocalId),
                stageRepository.observeStages(projectLocalId),
            ) { detail, members, stages -> Triple(detail, members, stages) }
                .collect { (detail, members, stages) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            canEdit = detail != null && canEdit(detail, members),
                            stages = stages,
                        )
                    }
                }
        }
        viewModelScope.launch { projectRepository.refreshProject(projectLocalId) }
    }

    fun retry() {
        localId?.let { id -> viewModelScope.launch { projectRepository.refreshProject(id) } }
    }

    fun deleteProject() {
        val id = localId ?: return
        if (_state.value.isDeleting || _state.value.deleted) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            // Repo: an unsynced create is dropped locally right away; otherwise it's
            // marked PENDING/DELETE and pushed on the next sync (Phase D / ADR-21).
            runCatching { projectRepository.deleteProject(id) }
            _state.update { it.copy(isDeleting = false, deleted = true) }
        }
    }

    private fun canEdit(detail: ProjectDetail, members: List<ProjectMember>): Boolean {
        val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id
        return projectAdmin(detail.ownerId, members, currentUserId)
    }
}
