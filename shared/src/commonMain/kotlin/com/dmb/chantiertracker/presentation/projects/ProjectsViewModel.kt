package com.dmb.chantiertracker.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.domain.model.applySort
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<Project> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && projects.isEmpty()
}

class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
    private val sortHolder: ProjectSortHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    val sort: StateFlow<ProjectSort> = sortHolder.sort

    init {
        viewModelScope.launch {
            combine(projectRepository.observeProjects(), sortHolder.sort) { projects, sort ->
                ProjectsUiState(isLoading = false, projects = projects.applySort(sort))
            }.collect { _state.value = it }
        }
    }

    /** Called on each screen entry: kicks a background pull. The list itself always comes from the local store. */
    fun onEnter() {
        viewModelScope.launch { projectRepository.refresh() }
    }
}
