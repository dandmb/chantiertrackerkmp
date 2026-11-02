package com.dmb.chantiertracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectSortTest {

    private fun project(localId: String, updatedAt: Long?, serverId: Long?) =
        Project(localId, localId, null, null, ProjectStatus.IN_PROGRESS, createdAt = "2026-01-0${localId.length}", updatedAtMillis = updatedAt, serverId = serverId)

    @Test
    fun the_last_modified_project_comes_first_then_the_highest_id_like_the_api() {
        val projects = listOf(
            project("a", updatedAt = 100, serverId = 3),
            project("b", updatedAt = 300, serverId = 1),
            project("c", updatedAt = 100, serverId = 7),
            project("d", updatedAt = 200, serverId = 2),
        )

        assertEquals(listOf("b", "d", "c", "a"), projects.applySort(ProjectSort.NEWEST_FIRST).map { it.localId })
        assertEquals(listOf("a", "c", "d", "b"), projects.applySort(ProjectSort.OLDEST_FIRST).map { it.localId })
    }
}
