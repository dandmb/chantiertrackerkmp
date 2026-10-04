package com.dmb.chantiertracker.data.session

import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAppPreferences
import com.dmb.chantiertracker.support.FakeLocalDataEraser
import com.dmb.chantiertracker.support.FakeUnsyncedWriteCounter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalDataOwnershipTest {

    private val alice = User(1, "alice@chantier.dev", "Alice", true, GlobalRole.USER)
    private val bob = User(2, "bob@chantier.dev", "Bob", true, GlobalRole.SUPER_ADMIN)

    private class Fixture(unsynced: Int = 0) {
        val preferences = FakeAppPreferences()
        val eraser = FakeLocalDataEraser()
        val counter = FakeUnsyncedWriteCounter(unsynced)
        val store = LocalDataOwnerStore(preferences)
        val ownership = LocalDataOwnership(store, eraser, counter)
    }

    @Test
    fun the_owner_signing_in_again_never_erases() = runTest {
        val f = Fixture()
        f.store.record(alice)

        f.ownership.claim(alice, SessionStart.LOGIN)
        f.ownership.claim(alice, SessionStart.BOOTSTRAP)

        assertEquals(0, f.eraser.eraseCount)
    }

    @Test
    fun another_account_erases_then_becomes_the_owner() = runTest {
        val f = Fixture(unsynced = 5)
        f.store.record(alice)

        f.ownership.claim(bob, SessionStart.LOGIN)

        assertEquals(1, f.eraser.eraseCount, "even with unsent writes: they belong to Alice, never to Bob")
        assertEquals(bob, f.store.owner())
    }

    @Test
    fun another_account_found_at_start_erases_too() = runTest {
        val f = Fixture()
        f.store.record(alice)

        f.ownership.claim(bob, SessionStart.BOOTSTRAP)

        assertEquals(1, f.eraser.eraseCount)
    }

    @Test
    fun no_owner_and_a_continuing_session_adopts() = runTest {
        val f = Fixture()

        f.ownership.claim(alice, SessionStart.BOOTSTRAP)

        assertEquals(0, f.eraser.eraseCount)
        assertEquals(alice, f.store.owner())
    }

    @Test
    fun no_owner_and_a_fresh_sign_in_erases_when_nothing_is_unsent() = runTest {
        val f = Fixture(unsynced = 0)

        f.ownership.claim(alice, SessionStart.LOGIN)

        assertEquals(1, f.eraser.eraseCount)
    }

    @Test
    fun no_owner_and_a_fresh_sign_in_adopts_when_something_is_unsent() = runTest {
        val f = Fixture(unsynced = 2)

        f.ownership.claim(alice, SessionStart.LOGIN)

        assertEquals(0, f.eraser.eraseCount)
        assertEquals(alice, f.store.owner())
    }

    // The cached profile is what reopens the app offline (A-7): every field must survive.
    @Test
    fun the_recorded_profile_reads_back_identically() {
        val store = LocalDataOwnerStore(FakeAppPreferences())

        assertNull(store.owner())
        store.record(bob)

        assertEquals(bob, store.owner())
    }
}
