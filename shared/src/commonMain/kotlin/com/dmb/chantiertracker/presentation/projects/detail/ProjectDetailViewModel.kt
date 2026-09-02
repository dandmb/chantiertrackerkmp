package com.dmb.chantiertracker.presentation.projects.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val isLoading: Boolean = true,
    val detail: ProjectDetail? = null,
    val canEdit: Boolean = false,
    val error: DomainException? = null,
)

class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectDetailUiState())
    val state = _state.asStateFlow()

    private var projectId: Long? = null

    fun load(id: Long) {
        projectId = id
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = projectRepository.getProject(id)
                _state.update { it.copy(isLoading = false, detail = detail, canEdit = resolveCanEdit(detail)) }
            } catch (e: DomainException) {
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    fun retry() {
        projectId?.let(::load)
    }

    private suspend fun resolveCanEdit(detail: ProjectDetail): Boolean {
        val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id ?: return false
        if (detail.ownerId == currentUserId) return true
        val role = runCatching { projectRepository.getMembers(detail.id) }
            .getOrNull()
            ?.firstOrNull { it.userId == currentUserId }
            ?.role
        return role == ProjectRole.ADMIN
    }
}
