package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.AppPreferences
import com.dmb.chantiertracker.data.session.LocalDataEraser
import com.dmb.chantiertracker.data.session.LocalDataOwnerStore
import com.dmb.chantiertracker.data.session.LocalDataOwnership
import com.dmb.chantiertracker.data.session.UnsyncedWriteCounter
import com.dmb.chantiertracker.domain.repository.SignOutRepository
import com.dmb.chantiertracker.domain.repository.SignOutResult

class FakeLocalDataEraser(private val onErase: (suspend () -> Unit)? = null) : LocalDataEraser {
    var eraseCount = 0
        private set

    override suspend fun eraseAll() {
        eraseCount++
        onErase?.invoke()
    }
}

class FakeUnsyncedWriteCounter(var count: Int = 0) : UnsyncedWriteCounter {
    /** Successive answers, consumed one per call; [count] once exhausted. */
    val answers = ArrayDeque<Int>()

    override suspend fun countUnsynced(): Int = answers.removeFirstOrNull() ?: count
}

fun testOwnership(
    preferences: AppPreferences = FakeAppPreferences(),
    eraser: LocalDataEraser = FakeLocalDataEraser(),
    unsynced: UnsyncedWriteCounter = FakeUnsyncedWriteCounter(),
) = LocalDataOwnership(LocalDataOwnerStore(preferences), eraser, unsynced)

class FakeSignOutRepository : SignOutRepository {
    val results = ArrayDeque<SignOutResult>()
    val calls = mutableListOf<Boolean>()

    override suspend fun signOut(acceptRefusedWrites: Boolean): SignOutResult {
        calls += acceptRefusedWrites
        return results.removeFirstOrNull() ?: SignOutResult.SignedOut
    }
}
