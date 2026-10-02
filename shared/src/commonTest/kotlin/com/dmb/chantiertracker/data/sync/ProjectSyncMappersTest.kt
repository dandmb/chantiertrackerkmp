package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.remote.dto.ProjectDetailDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import com.dmb.chantiertracker.data.repository.toProjectDetail
import com.dmb.chantiertracker.support.founderOnFreeEntitlements
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.ownerEntitlements
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectSyncMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun detailDto(ownerFields: String) = json.decodeFromString<ProjectDetailDto>(
        """{"id":7,"name":"Villa","currency":"EUR","timezone":"Europe/Paris","ownerId":1,
            |"ownerPlan":"FREE",$ownerFields"status":"IN_PROGRESS","totalSpent":12.5}""".trimMargin(),
    )

    private val founderFields = """"ownerIsFounder":true,"ownerCanExportPdf":true,"ownerMaxHistoryDays":180,
        |"ownerMaxVideos":5,"ownerMaxVideoDurationSeconds":120,"ownerMaxSupervisorsPerProject":3,""".trimMargin()

    @Test
    fun the_owner_entitlements_sent_with_the_detail_are_stored_as_received() {
        val entity = detailDto(founderFields).toSyncedEntity("p1", syncedAt = 1_000L)

        assertEquals("FREE", entity.ownerPlan)
        assertEquals(true, entity.ownerIsFounder)
        assertEquals(true, entity.ownerCanExportPdf)
        assertEquals(180, entity.ownerMaxHistoryDays)
        assertEquals(5, entity.ownerMaxVideos)
        assertEquals(120, entity.ownerMaxVideoDurationSeconds)
        assertEquals(3, entity.ownerMaxSupervisorsPerProject)
        assertEquals(founderOnFreeEntitlements, entity.toProjectDetail().ownerEntitlements)
    }

    // The intermediate backend (founders program without the lot-2 fields) must
    // not be half-read: a missing ownerMaxSupervisorsPerProject would otherwise
    // be stored as "unlimited".
    @Test
    fun a_block_missing_the_video_and_supervisor_fields_is_not_taken_at_all() {
        val lot1Only = """"ownerIsFounder":true,"ownerCanExportPdf":true,"ownerMaxHistoryDays":180,"""

        val entity = detailDto(lot1Only).toSyncedEntity("p1", syncedAt = 1_000L)

        assertNull(entity.ownerIsFounder)
        assertNull(entity.ownerMaxSupervisorsPerProject)
        assertNull(entity.toProjectDetail().ownerEntitlements, "plan-based fallback, never a fabricated 'unlimited'")
    }

    // null is "unlimited" here: it must replace a previously stored 30, not be
    // read as "field absent, keep the old value".
    @Test
    fun an_unlimited_history_sent_by_the_server_overwrites_a_previously_stored_window() {
        val previous = localProject("p1").copy(
            ownerIsFounder = false, ownerCanExportPdf = false, ownerMaxHistoryDays = 30,
            ownerMaxVideos = 0, ownerMaxVideoDurationSeconds = 0, ownerMaxSupervisorsPerProject = 1,
        )
        val upgradedOwner = """"ownerIsFounder":false,"ownerCanExportPdf":true,"ownerMaxHistoryDays":null,
            |"ownerMaxVideos":20,"ownerMaxVideoDurationSeconds":300,"ownerMaxSupervisorsPerProject":null,""".trimMargin()

        val entity = detailDto(upgradedOwner).toSyncedEntity("p1", syncedAt = 1_000L, previous = previous)

        assertEquals(true, entity.ownerCanExportPdf)
        assertNull(entity.ownerMaxHistoryDays)
        assertNull(entity.ownerMaxSupervisorsPerProject, "unlimited replaces the previous cap of 1")
        assertEquals(
            ownerEntitlements(
                canExportPdf = true, maxHistoryDays = null,
                maxVideos = 20, maxVideoDurationSeconds = 300, maxSupervisorsPerProject = null,
            ),
            entity.toProjectDetail().ownerEntitlements,
        )
    }

    @Test
    fun a_backend_that_predates_the_founders_program_leaves_the_entitlements_unknown() {
        val entity = detailDto(ownerFields = "").toSyncedEntity("p1", syncedAt = 1_000L)

        assertNull(entity.ownerIsFounder)
        assertNull(entity.ownerCanExportPdf)
        assertNull(entity.toProjectDetail().ownerEntitlements, "the screens then fall back on ownerPlan")
        assertEquals("FREE", entity.ownerPlan)
    }

    @Test
    fun a_detail_without_the_entitlements_keeps_what_an_earlier_pull_stored() {
        val previous = localProject("p1").copy(
            ownerIsFounder = true, ownerCanExportPdf = true, ownerMaxHistoryDays = 180,
            ownerMaxVideos = 5, ownerMaxVideoDurationSeconds = 120, ownerMaxSupervisorsPerProject = 3,
        )

        val entity = detailDto(ownerFields = "").toSyncedEntity("p1", syncedAt = 1_000L, previous = previous)

        assertEquals(true, entity.ownerIsFounder)
        assertEquals(true, entity.ownerCanExportPdf)
        assertEquals(180, entity.ownerMaxHistoryDays)
        assertEquals(founderOnFreeEntitlements, entity.toProjectDetail().ownerEntitlements)
    }

    // The list endpoint carries no owner field at all: a list refresh must not
    // wipe what the detail pull stored.
    @Test
    fun the_project_list_pull_keeps_the_entitlements_stored_by_the_detail_pull() {
        val previous = localProject("p1").copy(
            ownerPlan = "FREE", ownerIsFounder = true, ownerCanExportPdf = true, ownerMaxHistoryDays = 180,
            ownerMaxVideos = 5, ownerMaxVideoDurationSeconds = 120, ownerMaxSupervisorsPerProject = 3,
        )
        val listDto = ProjectDto(id = 7, name = "Villa", status = "IN_PROGRESS")

        val entity = listDto.toSyncedEntity("p1", syncedAt = 1_000L, previous = previous)

        assertEquals("FREE", entity.ownerPlan)
        assertEquals(true, entity.ownerIsFounder)
        assertEquals(true, entity.ownerCanExportPdf)
        assertEquals(180, entity.ownerMaxHistoryDays)
        assertEquals(founderOnFreeEntitlements, entity.toProjectDetail().ownerEntitlements)
    }

    @Test
    fun a_project_never_pulled_in_detail_has_no_entitlements() {
        assertNull(localProject("p1").toProjectDetail().ownerEntitlements)
    }
}
