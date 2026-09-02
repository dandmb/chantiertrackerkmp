package com.dmb.chantiertracker.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<Project> = emptyList(),
    val error: DomainException? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && projects.isEmpty()
}

class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val projects = projectRepository.getProjects()
                _state.update { it.copy(isLoading = false, projects = projects) }
            } catch (e: DomainException) {
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }
}
