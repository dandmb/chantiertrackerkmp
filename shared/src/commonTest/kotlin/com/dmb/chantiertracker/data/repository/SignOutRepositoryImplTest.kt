package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.SignOutResult
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeUnsyncedWriteCounter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignOutRepositoryImplTest {

    private class Fixture(vararg counts: Int, outcome: SyncOutcome = SyncOutcome.Synced) {
        val auth = FakeAuthRepository()
        val syncer = FakeSyncer().apply { this.outcome = outcome }
        val counter = FakeUnsyncedWriteCounter().apply { answers.addAll(counts.toList()) }
        val repo = SignOutRepositoryImpl(auth, syncer, counter)
        val signedOut get() = "logout" in auth.calls
    }

    @Test
    fun nothing_unsent_signs_out_at_once_without_a_sync() = runTest {
        val f = Fixture(0)

        assertEquals(SignOutResult.SignedOut, f.repo.signOut())
        assertTrue(f.signedOut)
        assertEquals(0, f.syncer.syncCount)
    }

    @Test
    fun unsent_writes_that_the_sync_manages_to_send_let_the_sign_out_through() = runTest {
        val f = Fixture(3, 0)

        assertEquals(SignOutResult.SignedOut, f.repo.signOut())
        assertEquals(1, f.syncer.syncCount)
        assertTrue(f.signedOut)
    }

    @Test
    fun offline_with_unsent_writes_blocks_the_sign_out() = runTest {
        val f = Fixture(3, 3, outcome = SyncOutcome.Skipped)

        assertEquals(SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 3)), f.repo.signOut())
        assertTrue(!f.signedOut, "the session and the token are kept")
    }

    @Test
    fun a_failing_server_with_unsent_writes_blocks_the_sign_out_too() = runTest {
        val f = Fixture(2, 2, outcome = SyncOutcome.Failed(DomainException.Unexpected))

        assertEquals(SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), f.repo.signOut())
        assertTrue(!f.signedOut)
    }

    @Test
    fun writes_left_after_a_successful_sync_are_reported_as_refused_not_blocking() = runTest {
        val f = Fixture(4, 1)

        assertEquals(SignOutResult.RefusedWritesLeft(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 1)), f.repo.signOut())
        assertTrue(!f.signedOut, "the user is asked first")
    }

    @Test
    fun confirming_lets_the_sign_out_through_despite_refused_writes() = runTest {
        val f = Fixture(1, 1)

        assertEquals(SignOutResult.SignedOut, f.repo.signOut(acceptRefusedWrites = true))
        assertTrue(f.signedOut)
    }

    @Test
    fun confirming_never_skips_writes_that_could_still_be_sent() = runTest {
        val f = Fixture(1, 1, outcome = SyncOutcome.Skipped)

        assertEquals(SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 1)), f.repo.signOut(acceptRefusedWrites = true))
        assertTrue(!f.signedOut)
    }

    @Test
    fun no_network_with_unsent_writes_blocks_the_sign_out() = runTest {
        val f = Fixture(2, 2, outcome = SyncOutcome.Failed(DomainException.Network))

        assertEquals(SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), f.repo.signOut())
    }

    @Test
    fun a_rate_limited_sync_blocks_the_sign_out_like_an_unreachable_server() = runTest {
        val f = Fixture(2, 2, outcome = SyncOutcome.Failed(DomainException.RateLimited(30)))

        assertEquals(SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), f.repo.signOut())
    }

    @Test
    fun a_sync_the_server_refuses_leaves_the_writes_as_refused_with_a_way_out() = runTest {
        val f = Fixture(2, 2, outcome = SyncOutcome.Failed(DomainException.NotFound))

        assertEquals(SignOutResult.RefusedWritesLeft(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), f.repo.signOut(), "never a dead end: the user can still sign out (B-1)")
        assertEquals(SignOutResult.SignedOut, Fixture(2, 2, outcome = SyncOutcome.Failed(DomainException.Forbidden)).repo.signOut(acceptRefusedWrites = true))
    }

    @Test
    fun what_is_left_unsent_is_reported_by_kind_as_the_counter_gives_it() = runTest {
        val byKind = com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 1, lines = 2, attachments = 1)
        val counter = object : com.dmb.chantiertracker.data.session.UnsyncedWriteCounter {
            override suspend fun countUnsynced(): Int = 6
            override suspend fun unsentByKind() = byKind
        }
        val auth = FakeAuthRepository()

        val refused = SignOutRepositoryImpl(auth, FakeSyncer().apply { outcome = SyncOutcome.Synced }, counter).signOut()
        val offline = SignOutRepositoryImpl(auth, FakeSyncer().apply { outcome = SyncOutcome.Skipped }, counter).signOut()

        assertEquals(SignOutResult.RefusedWritesLeft(byKind), refused, "ghost parents are not in the count: 4, not 6")
        assertEquals(SignOutResult.Blocked(byKind), offline)
    }

    @Test
    fun only_ghost_parents_left_do_not_hold_the_sign_out() = runTest {
        val counter = object : com.dmb.chantiertracker.data.session.UnsyncedWriteCounter {
            override suspend fun countUnsynced(): Int = 2
            override suspend fun unsentByKind() = com.dmb.chantiertracker.domain.model.UnsentWrites()
        }
        val auth = FakeAuthRepository()

        assertEquals(SignOutResult.SignedOut, SignOutRepositoryImpl(auth, FakeSyncer(), counter).signOut())
        assertTrue("logout" in auth.calls)
    }
}
