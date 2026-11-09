package com.dmb.chantiertracker.presentation.sync

import com.dmb.chantiertracker.data.session.PendingWriteCounter
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineBannerViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() = resetTestMainDispatcher()

    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val pending = MutableStateFlow(0)
    private val vm by lazy { OfflineBannerViewModel(connectivity, PendingWriteCounter { pending }) }

    @Test
    fun online_shows_no_banner() = runTest {
        vm.state.launchIn(backgroundScope)
        pending.value = 2
        advanceUntilIdle()

        assertEquals(OfflineBannerState(offline = false, pending = 2), vm.state.value)
    }

    @Test
    fun going_offline_shows_the_banner_with_the_writes_waiting_to_be_sent() = runTest {
        vm.state.launchIn(backgroundScope)
        connectivity.setOnline(false)
        pending.value = 3
        advanceUntilIdle()

        assertEquals(OfflineBannerState(offline = true, pending = 3), vm.state.value)

        connectivity.setOnline(true)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.offline)
    }
}
