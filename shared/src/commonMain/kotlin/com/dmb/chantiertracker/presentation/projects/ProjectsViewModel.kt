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
    val isRefreshing: Boolean = false,
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

    private val _isRefreshing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                projectRepository.observeProjects(),
                sortHolder.sort,
                _isRefreshing,
            ) { projects, sort, isRefreshing ->
                ProjectsUiState(isLoading = false, projects = projects.applySort(sort), isRefreshing = isRefreshing)
            }.collect { _state.value = it }
        }
    }

    /** Called on each screen entry: kicks a silent background pull. The list itself always comes from the local store. */
    fun onEnter() {
        viewModelScope.launch { projectRepository.refresh() }
    }

    /** Pull-to-refresh: the same background pull, but with a visible indicator until it settles. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                projectRepository.refresh()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
