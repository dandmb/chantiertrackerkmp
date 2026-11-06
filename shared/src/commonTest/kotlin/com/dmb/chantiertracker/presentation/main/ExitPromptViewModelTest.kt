package com.dmb.chantiertracker.presentation.main

import com.dmb.chantiertracker.data.session.UnsyncedWriteCounter
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ExitPromptViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private class Counter(var unsent: UnsentWrites) : UnsyncedWriteCounter {
        override suspend fun countUnsynced(): Int = unsent.total
        override suspend fun unsentByKind(): UnsentWrites = unsent
    }

    @Test
    fun no_dialog_until_back_is_pressed_on_the_root_screen() = runTest {
        val vm = ExitPromptViewModel(Counter(UnsentWrites()))
        advanceUntilIdle()
        assertNull(vm.prompt.value)
    }

    @Test
    fun the_dialog_counts_every_change_not_sent_yet() = runTest {
        val vm = ExitPromptViewModel(Counter(UnsentWrites(projects = 1, entries = 1, attachments = 1)))
        vm.request()
        advanceUntilIdle()
        assertEquals(ExitPrompt(unsentChanges = 3), vm.prompt.value)

        vm.dismiss()
        assertNull(vm.prompt.value)
    }

    @Test
    fun the_count_is_read_again_at_each_press() = runTest {
        val counter = Counter(UnsentWrites(entries = 2))
        val vm = ExitPromptViewModel(counter)
        vm.request()
        advanceUntilIdle()
        vm.dismiss()

        counter.unsent = UnsentWrites()
        vm.request()
        advanceUntilIdle()
        assertEquals(ExitPrompt(unsentChanges = 0), vm.prompt.value)
    }
}
