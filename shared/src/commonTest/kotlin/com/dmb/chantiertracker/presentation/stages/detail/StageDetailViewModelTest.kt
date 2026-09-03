package com.dmb.chantiertracker.presentation.stages.detail

import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StageDetailViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun detail() = StageDetail(
        localId = "s1", projectLocalId = "p1", name = "Gros œuvre",
        description = "Fondations et murs", estimatedBudget = 18000.0,
        startDate = "2026-02-01", endDate = "2026-05-01", status = StageStatus.IN_PROGRESS,
    )

    private fun projectRepo(currency: String? = "EUR") = FakeProjectRepository(
        detail = currency?.let {
            ProjectDetail(
                localId = "p1", name = "Villa", description = null, location = null,
                currency = it, timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = 1,
            )
        },
    )

    private fun vm(
        stages: FakeStageRepository,
        projects: FakeProjectRepository = projectRepo(),
    ) = StageDetailViewModel(stages, projects)

    @Test
    fun observes_the_stage_and_its_project_currency_then_kicks_a_pull() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val v = vm(repo, projectRepo(currency = "EUR"))
        v.load("s1")
        advanceUntilIdle()

        val state = v.state.value
        assertFalse(state.isLoading)
        assertEquals("Gros œuvre", state.detail?.name)
        assertEquals(18000.0, state.detail?.estimatedBudget)
        assertEquals("EUR", state.currency, "currency comes from the parent project")
        assertEquals(1, repo.refreshStageCount)
        assertEquals(listOf("refreshStage:s1"), repo.log)
    }

    @Test
    fun currency_is_null_when_the_project_has_none() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val v = vm(repo, projectRepo(currency = null))
        v.load("s1")
        advanceUntilIdle()

        assertNull(v.state.value.currency)
    }

    @Test
    fun a_missing_stage_is_flagged_once_loading_settles() = runTest {
        val repo = FakeStageRepository(detail = null)
        val v = vm(repo)
        v.load("gone")
        advanceUntilIdle()

        assertFalse(v.state.value.isLoading)
        assertTrue(v.state.value.isMissing)
    }

    @Test
    fun retry_pulls_this_stage_again() = runTest {
        val repo = FakeStageRepository(detail = null)
        val v = vm(repo)
        v.load("s1")
        advanceUntilIdle()

        v.retry()
        advanceUntilIdle()

        assertEquals(2, repo.refreshStageCount)
    }

    @Test
    fun the_stage_reappearing_in_the_store_clears_the_missing_state() = runTest {
        val repo = FakeStageRepository(detail = null)
        val v = vm(repo)
        v.load("s1")
        advanceUntilIdle()
        assertTrue(v.state.value.isMissing)

        repo.detailFlow.value = detail()
        advanceUntilIdle()

        assertFalse(v.state.value.isMissing)
        assertEquals("Gros œuvre", v.state.value.detail?.name)
    }
}
