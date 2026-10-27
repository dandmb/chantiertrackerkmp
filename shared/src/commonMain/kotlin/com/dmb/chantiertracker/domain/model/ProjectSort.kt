package com.dmb.chantiertracker.domain.model

enum class ProjectSort { NEWEST_FIRST, OLDEST_FIRST }

private val lastModifiedFirst: Comparator<Project> =
    compareByDescending<Project> { it.updatedAtMillis ?: Long.MAX_VALUE }.thenByDescending { it.serverId ?: Long.MAX_VALUE }

fun List<Project>.applySort(sort: ProjectSort): List<Project> = when (sort) {
    ProjectSort.NEWEST_FIRST -> sortedWith(lastModifiedFirst)
    ProjectSort.OLDEST_FIRST -> sortedWith(lastModifiedFirst.reversed())
}
