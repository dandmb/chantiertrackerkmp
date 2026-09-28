package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.AdminStats
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.model.StatsPoint
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_stats_date_range_future
import com.dmb.chantiertracker.support.FakeAdminRepository
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
class AdminStatsViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val sampleStats = AdminStats(
        totalUsers = 10, totalProjects = 4,
        registrations = listOf(StatsPoint("2026-09-01", 3)),
        projectsCreated = listOf(StatsPoint("2026-09-01", 1)),
    )

    @Test
    fun loading_fetches_the_default_granularity_with_no_date_bounds() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats }
        val vm = AdminStatsViewModel(repo)

        vm.load()
        advanceUntilIdle()

        assertEquals(listOf(Triple<Granularity, String?, String?>(Granularity.MONTH, null, null)), repo.getStatsCalls)
        assertEquals(sampleStats, vm.state.value.stats)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun changing_granularity_refetches() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats }
        val vm = AdminStatsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.setGranularity(Granularity.DAY)
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple<Granularity, String?, String?>(Granularity.MONTH, null, null),
                Triple<Granularity, String?, String?>(Granularity.DAY, null, null),
            ),
            repo.getStatsCalls,
        )
    }

    @Test
    fun setting_the_same_granularity_again_does_not_refetch() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats }
        val vm = AdminStatsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.setGranularity(Granularity.MONTH)
        advanceUntilIdle()

        assertEquals(1, repo.getStatsCalls.size)
    }

    @Test
    fun changing_the_date_range_sends_blank_fields_as_null() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats }
        val vm = AdminStatsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.onFromChange("2026-08-01")
        advanceUntilIdle()
        vm.onToChange("2026-09-01")
        advanceUntilIdle()

        assertEquals(Triple(Granularity.MONTH, "2026-08-01", "2026-09-01"), repo.getStatsCalls.last())
    }

    @Test
    fun an_end_date_in_the_future_blocks_the_fetch_and_shows_an_inline_error() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats }
        val vm = AdminStatsViewModel(repo)
        vm.load()
        advanceUntilIdle()
        val callsBefore = repo.getStatsCalls.size

        vm.onToChange("9999-01-01")
        advanceUntilIdle()

        assertEquals(callsBefore, repo.getStatsCalls.size, "no request should be sent while the range is invalid")
        assertEquals(
            Res.string.admin_stats_date_range_future,
            vm.state.value.dateRangeError,
        )
    }

    @Test
    fun fixing_an_invalid_range_clears_the_error_and_fetches_again() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats }
        val vm = AdminStatsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.onToChange("9999-01-01")
        advanceUntilIdle()
        assertTrue(vm.state.value.dateRangeError != null)

        vm.onToChange("2026-09-01")
        advanceUntilIdle()

        assertNull(vm.state.value.dateRangeError)
        assertEquals(Triple<Granularity, String?, String?>(Granularity.MONTH, null, "2026-09-01"), repo.getStatsCalls.last())
    }

    @Test
    fun retry_refetches_with_the_current_filters() = runTest {
        val repo = FakeAdminRepository().apply { statsResult = sampleStats; getStatsError = DomainException.Network }
        val vm = AdminStatsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        assertEquals(DomainException.Network, vm.state.value.error)

        repo.getStatsError = null
        vm.retry()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(sampleStats, vm.state.value.stats)
    }
}
