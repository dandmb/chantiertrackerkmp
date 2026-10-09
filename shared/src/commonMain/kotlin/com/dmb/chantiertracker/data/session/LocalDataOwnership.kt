package com.dmb.chantiertracker.data.session

import com.dmb.chantiertracker.data.local.AppPreferences
import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.ExportFileStore
import com.dmb.chantiertracker.data.local.db.LocalDataDao
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.domain.model.UnsentWrites

fun interface LocalDataEraser {
    suspend fun eraseAll()
}

fun interface UnsyncedWriteCounter {
    suspend fun countUnsynced(): Int

    suspend fun unsentByKind(): UnsentWrites = UnsentWrites(entries = countUnsynced())
}

class RoomUnsyncedWriteCounter(private val dao: LocalDataDao) : UnsyncedWriteCounter {
    override suspend fun countUnsynced(): Int = dao.countUnsynced()

    override suspend fun unsentByKind(): UnsentWrites = dao.countUnsentByKind().let {
        UnsentWrites(it.projects, it.stages, it.materials, it.entries, it.lines, it.attachments)
    }
}

enum class SessionStart { LOGIN, BOOTSTRAP }

/**
 * Erases everything this device holds for an account: Room, the attachment files, the cached PDF
 * exports, and the session-scoped state in memory (sync state, pending deep links).
 * Device preferences (language, theme, sort, onboarding flags) are kept.
 */
class LocalDataWiper(
    private val localDataDao: LocalDataDao,
    private val attachmentFileStore: AttachmentFileStore,
    private val exportFileStore: ExportFileStore,
    private val resetSessionState: () -> Unit,
) : LocalDataEraser {

    override suspend fun eraseAll() {
        localDataDao.eraseAll()
        attachmentFileStore.deleteAll()
        exportFileStore.deleteAll()
        resetSessionState()
    }
}

/**
 * The account that owns the local data, kept in the device preferences (so erasing Room never
 * forgets it), with the profile last confirmed by the server — what keeps a session usable when
 * the app is reopened offline (A-7).
 */
class LocalDataOwnerStore(private val preferences: AppPreferences) {

    fun owner(): User? {
        val id = preferences.read(KEY_ID)?.toLongOrNull() ?: return null
        return User(
            id = id,
            email = preferences.read(KEY_EMAIL).orEmpty(),
            name = preferences.read(KEY_NAME).orEmpty(),
            active = true,
            globalRole = GlobalRole.entries.firstOrNull { it.name == preferences.read(KEY_ROLE) } ?: GlobalRole.UNKNOWN,
        )
    }

    fun ownerId(): Long? = preferences.read(KEY_ID)?.toLongOrNull()

    fun record(user: User) {
        preferences.write(KEY_ID, user.id.toString())
        preferences.write(KEY_EMAIL, user.email)
        preferences.write(KEY_NAME, user.name)
        preferences.write(KEY_ROLE, user.globalRole.name)
    }

    private companion object {
        const val KEY_ID = "local_data_owner_id"
        const val KEY_EMAIL = "local_data_owner_email"
        const val KEY_NAME = "local_data_owner_name"
        const val KEY_ROLE = "local_data_owner_role"
    }
}

/**
 * A-1 (ADR-69): the local data belongs to one account. Must run under `Syncer.runExclusive`, with
 * the new account's token already stored, and before `Authenticated` is published — so no screen
 * and no sync pass ever sees the previous account's data with the new account's session.
 */
class LocalDataOwnership(
    private val store: LocalDataOwnerStore,
    private val eraser: LocalDataEraser,
    private val unsyncedWrites: UnsyncedWriteCounter,
) {

    fun cachedOwner(): User? = store.owner()

    suspend fun claim(user: User, start: SessionStart) {
        val ownerId = store.ownerId()
        when {
            ownerId == user.id -> Unit
            ownerId != null -> eraser.eraseAll()
            // No owner recorded yet: an install from before this mechanism. A session that simply
            // continues keeps its data; a fresh sign-in erases it unless something is still unsent,
            // which cannot be attributed and would otherwise be lost for good.
            start == SessionStart.LOGIN && unsyncedWrites.countUnsynced() == 0 -> eraser.eraseAll()
            else -> Unit
        }
        store.record(user)
    }
}
