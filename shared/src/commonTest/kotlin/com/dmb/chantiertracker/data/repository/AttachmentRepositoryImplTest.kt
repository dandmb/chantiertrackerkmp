package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeAttachmentDao
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeDailyEntryDao
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localDailyEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentRepositoryImplTest {

    private fun repo(
        dao: FakeAttachmentDao = FakeAttachmentDao(),
        fileStore: FakeAttachmentFileStore = FakeAttachmentFileStore(),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
        entryDao: FakeDailyEntryDao = FakeDailyEntryDao(),
        backend: FakeProjectBackend = FakeProjectBackend(),
    ) = AttachmentRepositoryImpl(dao, entryDao, backend.attachmentApi(), fileStore, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-attachment-id" })

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

    // ─── video (online only, ADR-35) ─────────────────────────────────────────

    @Test
    fun upload_video_sends_the_raw_file_then_stores_the_transcoded_mp4_locally_as_synced() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val entryDao = FakeDailyEntryDao(listOf(localDailyEntry("purchase-1", serverId = 909L)))
        val backend = FakeProjectBackend().apply { attachmentUploadDurationSeconds = 45 }

        val video = repo(dao = dao, fileStore = fileStore, entryDao = entryDao, backend = backend)
            .uploadVideo("purchase-1", ByteArray(4_000) { 7 }, "site.mov", "video/quicktime")

        assertTrue(video.isVideo)
        assertEquals("video/mp4", video.mimeType, "the server transcodes it")
        assertEquals(45, video.durationSeconds)

        val row = dao.findByLocalId(video.localId)!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus, "no PENDING queue — it uploaded now (ADR-35)")
        assertEquals(PendingOp.NONE, row.pendingOp)
        assertTrue(row.serverId != null)
        assertEquals(listOf(byteArrayOf(9, 9, 9).toList()), listOf(fileStore.readBytes(row.localPath).toList()), "the small transcoded file is what's kept locally")
        val created = backend.attachments.single()
        assertEquals("video/mp4", created.mimeType)
    }

    @Test
    fun upload_video_on_an_entry_that_has_not_synced_yet_fails_without_a_network_call() = runTest {
        val entryDao = FakeDailyEntryDao(listOf(localDailyEntry("purchase-1", serverId = null)))
        val backend = FakeProjectBackend()

        assertFailsWith<DomainException.NotFound> {
            repo(entryDao = entryDao, backend = backend).uploadVideo("purchase-1", byteArrayOf(1, 2), "v.mp4", "video/mp4")
        }
        assertTrue(backend.attachments.isEmpty())
    }

    @Test
    fun upload_video_surfaces_a_server_refusal() = runTest {
        val entryDao = FakeDailyEntryDao(listOf(localDailyEntry("purchase-1", serverId = 909L)))
        val backend = FakeProjectBackend().apply {
            attachmentUploadRejection = io.ktor.http.HttpStatusCode.Forbidden to
                "Vous avez atteint la limite de vidéos de votre plan."
        }

        assertFailsWith<DomainException.PlanLimitReached> {
            repo(entryDao = entryDao, backend = backend).uploadVideo("purchase-1", byteArrayOf(1, 2), "v.mp4", "video/mp4")
        }
    }
}
