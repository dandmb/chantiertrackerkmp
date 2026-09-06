package com.dmb.chantiertracker.presentation.stages.detail

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.todayIn
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeDailyLogRepository
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

    private fun detail(status: StageStatus = StageStatus.IN_PROGRESS) = StageDetail(
        localId = "s1", projectLocalId = "p1", name = "Gros œuvre",
        description = "Fondations et murs", estimatedBudget = 18000.0,
        startDate = "2026-02-01", endDate = "2026-05-01", status = status,
    )

    private fun projectRepo(
        currency: String? = "EUR",
        ownerId: Long? = 1L,
        status: ProjectStatus = ProjectStatus.IN_PROGRESS,
        timezone: String = "Europe/Paris",
        members: List<ProjectMember> = emptyList(),
    ) = FakeProjectRepository(
        detail = currency?.let {
            ProjectDetail(
                localId = "p1", name = "Villa", description = null, location = null,
                currency = it, timezone = timezone, status = status, ownerId = ownerId,
            )
        },
        members = members,
    )

    private fun auth(userId: Long = 1L) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(userId, "u@x.dev", "U", true, GlobalRole.USER)))
    }

    private fun vm(
        stages: FakeStageRepository,
        projects: FakeProjectRepository = projectRepo(),
        logs: FakeDailyLogRepository = FakeDailyLogRepository(),
        authRepo: FakeAuthRepository = auth(),
    ) = StageDetailViewModel(stages, projects, logs, authRepo)

    @Test
    fun observes_the_stage_and_its_project_currency_then_kicks_a_pull() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val logs = FakeDailyLogRepository()
        val v = vm(repo, projectRepo(currency = "EUR"), logs)
        v.load("s1")
        advanceUntilIdle()

        val state = v.state.value
        assertFalse(state.isLoading)
        assertEquals("Gros œuvre", state.detail?.name)
        assertEquals(18000.0, state.detail?.estimatedBudget)
        assertEquals("EUR", state.currency, "currency comes from the parent project")
        assertEquals(1, repo.refreshStageCount)
        assertEquals(listOf("refreshStage:s1"), repo.log)
        assertEquals(1, logs.refreshLogsCount)
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
    fun retry_pulls_this_stage_and_its_days_again() = runTest {
        val repo = FakeStageRepository(detail = null)
        val logs = FakeDailyLogRepository()
        val v = vm(repo, logs = logs)
        v.load("s1")
        advanceUntilIdle()

        v.retry()
        advanceUntilIdle()

        assertEquals(2, repo.refreshStageCount)
        assertEquals(2, logs.refreshLogsCount)
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

    // ─── write access for "add an entry today" (mirrors EntryWriteAccessService) ───

    @Test
    fun the_owner_can_add_today_even_on_a_suspended_project() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val v = vm(
            repo,
            projectRepo(ownerId = 7L, status = ProjectStatus.SUSPENDED),
            authRepo = auth(userId = 7L),
        )
        v.load("s1")
        advanceUntilIdle()

        assertTrue(v.state.value.canAddToday, "an ADMIN is never restricted")
    }

    @Test
    fun a_supervisor_can_add_today_while_the_project_and_stage_are_active() = runTest {
        val repo = FakeStageRepository(detail = detail(status = StageStatus.IN_PROGRESS))
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val v = vm(
            repo,
            projectRepo(ownerId = 1L, status = ProjectStatus.IN_PROGRESS, members = members),
            authRepo = auth(userId = 9L),
        )
        v.load("s1")
        advanceUntilIdle()

        assertTrue(v.state.value.canAddToday)
    }

    @Test
    fun a_supervisor_cannot_add_today_once_the_project_is_suspended() = runTest {
        val repo = FakeStageRepository(detail = detail(status = StageStatus.IN_PROGRESS))
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val v = vm(
            repo,
            projectRepo(ownerId = 1L, status = ProjectStatus.SUSPENDED, members = members),
            authRepo = auth(userId = 9L),
        )
        v.load("s1")
        advanceUntilIdle()

        assertFalse(v.state.value.canAddToday)
    }

    @Test
    fun a_supervisor_cannot_add_today_once_the_stage_is_completed() = runTest {
        val repo = FakeStageRepository(detail = detail(status = StageStatus.COMPLETED))
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val v = vm(
            repo,
            projectRepo(ownerId = 1L, status = ProjectStatus.IN_PROGRESS, members = members),
            authRepo = auth(userId = 9L),
        )
        v.load("s1")
        advanceUntilIdle()

        assertFalse(v.state.value.canAddToday)
    }

    // ─── today's date + the daily logs list ────────────────────────────────

    @Test
    fun today_date_is_resolved_in_the_projects_own_timezone() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val v = vm(repo, projectRepo(timezone = "Africa/Douala"))
        v.load("s1")
        advanceUntilIdle()

        assertEquals(todayIn("Africa/Douala").toString(), v.state.value.todayDate)
    }

    @Test
    fun logs_come_from_the_daily_log_repository() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val logs = FakeDailyLogRepository(
            logs = listOf(DailyLog("log-1", "s1", "2026-09-01", hasPurchase = true, hasWork = false)),
        )
        val v = vm(repo, logs = logs)
        v.load("s1")
        advanceUntilIdle()

        assertEquals(listOf("log-1"), v.state.value.logs.map { it.localId })
    }

    // ─── adding today's entry ───────────────────────────────────────────────

    @Test
    fun add_today_entry_delegates_to_the_daily_log_repository_with_the_resolved_date() = runTest {
        val repo = FakeStageRepository(detail = detail())
        val logs = FakeDailyLogRepository().apply { createdPurchaseDayId = "log-created" }
        val v = vm(repo, projectRepo(timezone = "Europe/Paris"), logs)
        v.load("s1")
        advanceUntilIdle()

        val result = v.addTodayEntry(EntryType.PURCHASE)

        assertEquals("log-created", result)
        assertEquals("createPurchaseEntry:s1:${todayIn("Europe/Paris")}", logs.log.last())
    }

    @Test
    fun add_today_entry_returns_null_before_the_stage_has_loaded() = runTest {
        val v = vm(FakeStageRepository(detail = null))
        assertNull(v.addTodayEntry(EntryType.PURCHASE))
    }
}
