package com.dmb.chantiertracker.domain.model

enum class ProjectSort { NEWEST_FIRST, OLDEST_FIRST }

/**
 * `GET /projects` ne garantit aucun ordre (pas d'`ORDER BY` côté backend) — on trie côté client
 * sur `createdAt` (timestamp ISO-8601, l'ordre lexical == l'ordre chronologique).
 */
fun List<Project>.applySort(sort: ProjectSort): List<Project> = when (sort) {
    ProjectSort.NEWEST_FIRST -> sortedByDescending { it.createdAt.orEmpty() }
    ProjectSort.OLDEST_FIRST -> sortedBy { it.createdAt.orEmpty() }
}
