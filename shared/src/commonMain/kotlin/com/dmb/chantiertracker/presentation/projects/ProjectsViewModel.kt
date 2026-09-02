package com.dmb.chantiertracker.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.domain.model.applySort
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val sortHolder: ProjectSortHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state = _state.asStateFlow()

    val sort: StateFlow<ProjectSort> = sortHolder.sort

    private var loadedOnce = false
    private var raw: List<Project> = emptyList()

    init {
        viewModelScope.launch {
            sortHolder.sort.collect { sort ->
                _state.update { it.copy(projects = raw.applySort(sort)) }
            }
        }
    }

    /** Called when the screen (re)enters the foreground: first pass shows the spinner, later passes refresh silently. */
    fun onEnter() {
        if (!loadedOnce) load() else refresh()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                raw = projectRepository.getProjects()
                loadedOnce = true
                _state.update { it.copy(isLoading = false, projects = raw.applySort(sortHolder.sort.value)) }
            } catch (e: DomainException) {
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                raw = projectRepository.getProjects()
                _state.update { it.copy(projects = raw.applySort(sortHolder.sort.value), error = null) }
            } catch (_: DomainException) {
                // keep showing the last known list rather than replacing it with an error
            }
        }
    }
}
