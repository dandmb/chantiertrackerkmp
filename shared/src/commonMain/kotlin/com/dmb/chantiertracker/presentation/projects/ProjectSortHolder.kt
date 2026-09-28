package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.ProjectSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Choix de tri de la liste des projets, conservé pour toute la session (singleton Koin).
 * Ni sauvegardé sur disque, ni remis à zéro par une recomposition ou une navigation.
 */
class ProjectSortHolder {
    private val _sort = MutableStateFlow(ProjectSort.NEWEST_FIRST)
    val sort: StateFlow<ProjectSort> = _sort.asStateFlow()

    fun set(sort: ProjectSort) {
        _sort.value = sort
    }
}
