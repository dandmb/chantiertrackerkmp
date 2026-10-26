package com.dmb.chantiertracker.data.session

import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeExportFileStore
import com.dmb.chantiertracker.support.FakeLocalDataDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalDataWiperTest {

    @Test
    fun erasing_clears_room_the_attachment_files_the_exports_and_the_session_state() = runTest {
        val dao = FakeLocalDataDao(unsynced = 3)
        val files = FakeAttachmentFileStore()
        files.save(byteArrayOf(1, 2), "ticket.jpg")
        val exports = FakeExportFileStore()
        exports.save(byteArrayOf(1), "chantier.pdf")
        var resets = 0

        LocalDataWiper(dao, files, exports) { resets++ }.eraseAll()

        assertEquals(1, dao.eraseCount)
        assertTrue(files.storedPaths.isEmpty(), "attachment files deleted")
        assertTrue(exports.saved.isEmpty(), "cached exports deleted")
        assertEquals(1, resets, "sync state and pending deep links reset")
    }
}
