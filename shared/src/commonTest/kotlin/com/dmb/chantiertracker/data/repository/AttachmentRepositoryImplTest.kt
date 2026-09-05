package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.support.FakeAttachmentDao
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localAttachment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentRepositoryImplTest {

    private fun repo(
        dao: FakeAttachmentDao = FakeAttachmentDao(),
        fileStore: FakeAttachmentFileStore = FakeAttachmentFileStore(),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = AttachmentRepositoryImpl(dao, fileStore, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-attachment-id" })

    @Test
    fun observe_attachments_maps_stored_rows_and_hides_pending_deletes() = runTest {
        val dao = FakeAttachmentDao(
            listOf(
                localAttachment("a", entryLocalId = "e1", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localAttachment("b", entryLocalId = "e1", pendingOp = PendingOp.DELETE),
                localAttachment("c", entryLocalId = "other-entry"),
            ),
        )

        val attachments = repo(dao).observeAttachments("e1").first()

        assertEquals(listOf("a"), attachments.map { it.localId }, "other entries and pending deletes are hidden")
    }

    @Test
    fun add_attachment_saves_the_bytes_then_writes_a_pending_create_row() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val syncer = FakeSyncer()
        val bytes = byteArrayOf(1, 2, 3, 4)

        val attachment = repo(dao, fileStore, syncer, MutableClock(4_242L))
            .addAttachment("e1", bytes, "facture.jpg", "image/jpeg")

        assertEquals("fixed-attachment-id", attachment.localId)
        assertEquals("e1", attachment.entryLocalId)
        assertEquals("facture.jpg", attachment.originalName)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals(4L, attachment.sizeBytes)
        assertTrue(attachment.localPath in fileStore.storedPaths, "the bytes were actually handed to the file store")
        assertEquals(bytes.toList(), fileStore.readBytes(attachment.localPath).toList())

        val row = dao.findByLocalId(attachment.localId)!!
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.CREATE, row.pendingOp)
        assertEquals(4_242L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun delete_attachment_that_never_reached_the_server_is_dropped_immediately_and_frees_the_file() = runTest {
        val dao = FakeAttachmentDao(listOf(localAttachment("a1", localPath = "fake-attachments/a1.jpg", serverId = null, pendingOp = PendingOp.CREATE)))
        val fileStore = FakeAttachmentFileStore()
        val syncer = FakeSyncer()

        repo(dao, fileStore, syncer).deleteAttachment("a1")

        assertNull(dao.findByLocalId("a1"))
        assertTrue("fake-attachments/a1.jpg" in fileStore.deletedPaths)
        assertEquals(0, syncer.requestCount, "nothing to sync — it only ever existed locally")
    }

    @Test
    fun delete_attachment_that_reached_the_server_is_marked_pending_delete_but_the_local_copy_is_freed_right_away() = runTest {
        val dao = FakeAttachmentDao(listOf(localAttachment("a1", localPath = "fake-attachments/a1.jpg", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)))
        val fileStore = FakeAttachmentFileStore()
        val syncer = FakeSyncer()

        repo(dao, fileStore, syncer).deleteAttachment("a1")

        val row = dao.findByLocalId("a1")!!
        assertEquals(PendingOp.DELETE, row.pendingOp)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertTrue("fake-attachments/a1.jpg" in fileStore.deletedPaths, "the bytes are no longer needed once the row is hidden from observeAttachments")
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun delete_attachment_ignores_an_unknown_local_id() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val syncer = FakeSyncer()

        repo(dao, fileStore, syncer).deleteAttachment("missing")

        assertEquals(0, syncer.requestCount)
        assertTrue(fileStore.deletedPaths.isEmpty())
    }
}
