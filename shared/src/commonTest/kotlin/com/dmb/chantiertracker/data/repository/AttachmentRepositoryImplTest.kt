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
import com.dmb.chantiertracker.support.fakeUploadFile
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localDailyEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    // ─── video (queued like a photo, sent by the engine) ─────────────────────

    private class VideoThatMustNotBeOpened(private val bytes: Long) : com.dmb.chantiertracker.domain.model.UploadFile {
        var opened = 0
        override val name = "chantier.mov"
        override val mimeType = "video/quicktime"
        override fun size(): Long = bytes
        override fun openSource(): kotlinx.io.RawSource {
            opened++
            return kotlinx.io.Buffer()
        }
    }

    private class SourceCutAfter(private val bytesBeforeTheCut: Int) : kotlinx.io.RawSource {
        private var given = 0
        override fun readAtMostTo(sink: kotlinx.io.Buffer, byteCount: Long): Long {
            if (given >= bytesBeforeTheCut) throw kotlinx.io.IOException("the process died during the copy")
            val now = minOf(byteCount.toInt(), bytesBeforeTheCut - given, 512)
            sink.write(ByteArray(now) { 7 })
            given += now
            return now.toLong()
        }
        override fun close() = Unit
    }

    @Test
    fun a_video_is_copied_locally_and_queued_like_a_photo_without_any_network_call() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val syncer = FakeSyncer()
        val entryDao = FakeDailyEntryDao(listOf(localDailyEntry("purchase-1", serverId = null)))
        val backend = FakeProjectBackend()
        val bytes = ByteArray(4_000) { 7 }

        val added = runCatching {
            repo(dao, fileStore, syncer, entryDao = entryDao, backend = backend).addVideo("purchase-1", fakeUploadFile(bytes, name = "site.mov", mimeType = "video/quicktime"))
        }

        assertEquals(null, added.exceptionOrNull(), "even on an entry the server does not know yet")
        val row = dao.findByLocalId(added.getOrThrow().localId)!!
        assertEquals(
            listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null, "site.mov", "video/quicktime", 4_000L),
            listOf(row.syncStatus, row.pendingOp, row.serverId, row.originalName, row.mimeType, row.sizeBytes),
        )
        assertEquals(bytes.toList(), fileStore.readBytes(row.localPath).toList(), "the raw video is kept on the device until it is sent")
        assertEquals(emptyList(), backend.receivedMethods, "nothing is sent from here: the engine does it")
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun a_video_heavier_than_the_server_accepts_is_refused_before_any_copy() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val entryDao = FakeDailyEntryDao(listOf(localDailyEntry("purchase-1", serverId = 909L)))
        val backend = FakeProjectBackend()
        val tooHeavy = VideoThatMustNotBeOpened(com.dmb.chantiertracker.domain.model.MAX_VIDEO_BYTES + 1)

        val refused = runCatching { repo(dao, fileStore, entryDao = entryDao, backend = backend).addVideo("purchase-1", tooHeavy) }

        assertEquals<Any?>(DomainException.FileTooLarge, refused.exceptionOrNull())
        assertEquals(listOf<Any>(0, emptySet<String>(), 0, emptyList<String>()), listOf(tooHeavy.opened, fileStore.storedPaths, dao.count(), backend.receivedMethods), "not read, not copied, not queued, not sent")
    }

    @Test
    fun a_photo_above_forty_megapixels_is_refused_when_it_is_taken_before_any_copy() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val backend = FakeProjectBackend()
        val syncer = FakeSyncer()

        val refused = runCatching {
            repo(dao, fileStore, syncer, backend = backend).addAttachment("purchase-1", com.dmb.chantiertracker.data.local.aJpegHeader(8000, 6000), "chantier.jpg", "image/jpeg")
        }

        assertEquals<Any?>(DomainException.ImageTooLarge, refused.exceptionOrNull())
        assertEquals(listOf<Any>(emptySet<String>(), 0, emptyList<String>(), 0), listOf(fileStore.storedPaths, dao.count(), backend.receivedMethods, syncer.requestCount), "not copied, not queued, not sent")
    }

    @Test
    fun a_photo_of_forty_megapixels_or_of_unknown_size_is_queued_as_usual() = runTest {
        val atTheLimit = FakeAttachmentDao()
        val unknownSize = FakeAttachmentDao()

        repo(atTheLimit).addAttachment("purchase-1", com.dmb.chantiertracker.data.local.aJpegHeader(8000, 5000), "limite.jpg", "image/jpeg")
        repo(unknownSize).addAttachment("purchase-1", byteArrayOf(1, 2, 3), "inconnue.heic", "image/heic")

        assertEquals(listOf(1, 1), listOf(atTheLimit.count(), unknownSize.count()))
    }

    @Test
    fun a_video_of_exactly_the_server_limit_is_accepted() = runTest {
        val dao = FakeAttachmentDao()
        val atTheLimit = object : com.dmb.chantiertracker.domain.model.UploadFile by fakeUploadFile(byteArrayOf(1, 2, 3), name = "limite.mp4") {
            override fun size(): Long = com.dmb.chantiertracker.domain.model.MAX_VIDEO_BYTES
        }

        val added = runCatching { repo(dao).addVideo("purchase-1", atTheLimit) }

        assertEquals(null, added.exceptionOrNull())
        assertEquals(1, dao.count())
    }

    @Test
    fun the_server_limit_for_a_video_is_200_megabytes() {
        assertEquals(200L * 1024 * 1024, com.dmb.chantiertracker.domain.model.MAX_VIDEO_BYTES)
    }

    @Test
    fun a_copy_cut_short_queues_nothing() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val syncer = FakeSyncer()
        val cut = object : com.dmb.chantiertracker.domain.model.UploadFile {
            override val name = "chantier.mov"
            override val mimeType = "video/quicktime"
            override fun size(): Long = 10_000
            override fun openSource(): kotlinx.io.RawSource = SourceCutAfter(2_000)
        }

        val added = runCatching { repo(dao, fileStore, syncer).addVideo("purchase-1", cut) }

        assertTrue(added.isFailure, "the copy did not finish")
        assertEquals(0, dao.count(), "no row points to half a video")
        assertEquals(0, syncer.requestCount)
        assertEquals(listOf(2_000L), fileStore.storedPaths.map { fileStore.sizeOf(it) }, "what was written stays as a file nobody refers to, for the cleaner")
    }

    @Test
    fun leaving_the_screen_during_the_copy_does_not_lose_the_video() = runTest {
        val dao = FakeAttachmentDao()
        val fileStore = FakeAttachmentFileStore()
        val copyStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val letTheCopyGoOn = kotlinx.coroutines.CompletableDeferred<Unit>()
        fileStore.beforeSavingFrom = {
            copyStarted.complete(Unit)
            letTheCopyGoOn.await()
        }
        val repository = repo(dao, fileStore)
        val screen = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val adding = screen.launch { runCatching { repository.addVideo("purchase-1", fakeUploadFile(ByteArray(3_000) { 7 }, name = "site.mov", mimeType = "video/quicktime")) } }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.withTimeoutOrNull(5_000) { copyStarted.await() } }

        adding.cancel()
        letTheCopyGoOn.complete(Unit)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeoutOrNull(5_000) { while (dao.count() == 0) kotlinx.coroutines.delay(10) }
        }

        assertEquals(1, dao.count(), "the video the user chose is queued even though the screen is gone")
        assertEquals(listOf(3_000L), fileStore.storedPaths.map { fileStore.sizeOf(it) })
    }

    @Test
    fun a_video_being_sent_shows_how_far_it_is_and_the_others_show_nothing() = runTest {
        val dao = FakeAttachmentDao(
            listOf(
                localAttachment("sending", entryLocalId = "e1"),
                localAttachment("waiting", entryLocalId = "e1"),
            ),
        )
        val syncer = FakeSyncer()
        syncer.progressOfUploads.value = mapOf("sending" to 0.4f)

        val attachments = repo(dao, syncer = syncer).observeAttachments("e1").first()

        assertEquals(mapOf<String, Float?>("sending" to 0.4f, "waiting" to null), attachments.associate { it.localId to it.uploadProgress })
    }

    @Test
    fun a_photo_is_compressed_before_it_is_queued_and_kept_as_a_jpeg() = runTest {
        val received = mutableListOf<Int>()
        val compressed = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)
        val repository = AttachmentRepositoryImpl(
            FakeAttachmentDao(), FakeDailyEntryDao(), FakeProjectBackend().attachmentApi(), FakeAttachmentFileStore(), FakeSyncer(), AppCoroutineScope(), MutableClock(2_000L),
            newLocalId = { "fixed-attachment-id" },
            photoCompressor = { photo -> received += photo.size; compressed },
        )

        val added = repository.addAttachment("e1", ByteArray(5_000), "IMG_0042.HEIC", "image/heic")

        assertEquals(listOf(5_000), received, "the photo went through the compressor before the queue")
        assertEquals(listOf("IMG_0042.jpg", "image/jpeg", 4L), listOf(added.originalName, added.mimeType, added.sizeBytes))
    }
}
