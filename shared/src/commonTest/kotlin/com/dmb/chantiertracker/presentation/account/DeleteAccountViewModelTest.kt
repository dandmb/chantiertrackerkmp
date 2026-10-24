package com.dmb.chantiertracker.presentation.account

import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AccountDeletionRepository
import com.dmb.chantiertracker.support.FakeConnectivityObserver
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

class FakeAccountDeletionRepository(var preview: AccountDeletionPreview = deletable) : AccountDeletionRepository {
    var previewCalls = 0
    val deletions = mutableListOf<String>()
    var failure: DomainException? = null

    override suspend fun preview(): AccountDeletionPreview {
        previewCalls++
        return preview
    }

    override suspend fun deleteAccount(password: String) {
        deletions += password
        failure?.let { throw it }
    }

    companion object {
        val deletable = AccountDeletionPreview(true, emptyList(), founder = false, ownedProjects = emptyList(), memberships = emptyList(), pendingInvitations = 0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() = resetTestMainDispatcher()

    @Test
    fun offline_nothing_is_read_and_nothing_can_be_sent() = runTest {
        val repo = FakeAccountDeletionRepository()
        val vm = DeleteAccountViewModel(repo, FakeConnectivityObserver(initiallyOnline = false))
        advanceUntilIdle()

        assertFalse(vm.state.value.online)
        assertEquals(0, repo.previewCalls, "the preview is a server read")
        vm.onPasswordChange("MonMotDePasse1!")
        vm.onUnderstoodChange(true)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        advanceUntilIdle()
        assertEquals(emptyList(), repo.deletions)
    }

    @Test
    fun back_online_the_preview_is_read() = runTest {
        val repo = FakeAccountDeletionRepository()
        val connectivity = FakeConnectivityObserver(initiallyOnline = false)
        val vm = DeleteAccountViewModel(repo, connectivity)
        advanceUntilIdle()

        connectivity.setOnline(true)
        advanceUntilIdle()

        assertEquals(1, repo.previewCalls)
        assertEquals(FakeAccountDeletionRepository.deletable, vm.state.value.preview)
    }

    @Test
    fun the_deletion_needs_the_password_and_the_explicit_checkbox() = runTest {
        val repo = FakeAccountDeletionRepository()
        val vm = DeleteAccountViewModel(repo, FakeConnectivityObserver(initiallyOnline = true))
        advanceUntilIdle()

        vm.onPasswordChange("MonMotDePasse1!")
        assertFalse(vm.state.value.canSubmit, "not without the checkbox")
        vm.onUnderstoodChange(true)
        vm.onPasswordChange("")
        assertFalse(vm.state.value.canSubmit, "not without the password")
        vm.onPasswordChange("MonMotDePasse1!")
        assertTrue(vm.state.value.canSubmit)

        vm.submit()
        advanceUntilIdle()
        assertEquals(listOf("MonMotDePasse1!"), repo.deletions)
    }

    @Test
    fun a_blocked_account_cannot_send_the_deletion() = runTest {
        val repo = FakeAccountDeletionRepository(FakeAccountDeletionRepository.deletable.copy(canDelete = false, blockers = listOf(DeletionBlocker.LAST_SUPER_ADMIN)))
        val vm = DeleteAccountViewModel(repo, FakeConnectivityObserver(initiallyOnline = true))
        advanceUntilIdle()

        vm.onPasswordChange("MonMotDePasse1!")
        vm.onUnderstoodChange(true)
        assertFalse(vm.state.value.canSubmit)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.sentWith(failure: DomainException): Pair<FakeAccountDeletionRepository, DeleteAccountViewModel> {
        val repo = FakeAccountDeletionRepository().apply { this.failure = failure }
        val vm = DeleteAccountViewModel(repo, FakeConnectivityObserver(initiallyOnline = true))
        advanceUntilIdle()
        vm.onPasswordChange("MonMotDePasse1!")
        vm.onUnderstoodChange(true)
        vm.submit()
        advanceUntilIdle()
        return repo to vm
    }

    @Test
    fun a_wrong_password_is_shown_on_the_field() = runTest {
        val (_, vm) = sentWith(DomainException.InvalidCurrentPassword)

        assertTrue(vm.state.value.wrongPassword)
        assertNull(vm.state.value.formError)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun a_refusal_with_a_blocker_gives_its_reason_and_reads_the_preview_again() = runTest {
        val (repo, vm) = sentWith(DomainException.AccountDeletionBlocked(DeletionBlocker.SUBSCRIPTION_STILL_RENEWING))

        assertEquals(DomainException.AccountDeletionBlocked(DeletionBlocker.SUBSCRIPTION_STILL_RENEWING), vm.state.value.formError)
        assertEquals(2, repo.previewCalls, "read again to show the blocker")
    }

    @Test
    fun too_many_attempts_keep_the_wait_to_show() = runTest {
        val (_, vm) = sentWith(DomainException.RateLimited(540))

        assertEquals(DomainException.RateLimited(540), vm.state.value.formError)
    }
}
