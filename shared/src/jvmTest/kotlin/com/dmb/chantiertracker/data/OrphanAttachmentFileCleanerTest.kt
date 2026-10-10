package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.OrphanAttachmentFileCleaner
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrphanAttachmentFileCleanerTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val fileStore = FakeAttachmentFileStore()
    private val syncer = FakeSyncer()

    @AfterTest fun close() = db.close()

    private suspend fun aDayToAttachTo() {
        db.projectDao().upsert(localProject("p1", name = "Villa", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("st1", projectLocalId = "p1", name = "Gros œuvre", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.dailyLogDao().upsert(localDailyLog("l1", stageLocalId = "st1", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e1", dailyLogLocalId = "l1", type = "PURCHASE", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    private suspend fun aFile(name: String) = fileStore.save(ByteArray(4), name)

    @Test
    fun a_file_no_row_references_is_removed_and_a_second_run_finds_nothing_left() = runTest {
        aDayToAttachTo()
        val kept = aFile("gardee.jpg")
        db.attachmentDao().upsert(localAttachment("a-synced", entryLocalId = "e1", localPath = kept, serverId = 70, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val orphan = aFile("orpheline.jpg")
        val otherOrphan = aFile("orpheline.mp4")
        val cleaner = OrphanAttachmentFileCleaner(fileStore, db.attachmentDao(), syncer, backgroundScope)

        assertEquals(setOf(orphan, otherOrphan), cleaner.removeOrphans().toSet())

        assertEquals(setOf(kept), fileStore.storedPaths)
        assertEquals(emptyList(), cleaner.removeOrphans())
        assertEquals(listOf(orphan, otherOrphan).sorted(), fileStore.deletedPaths.sorted(), "nothing is deleted twice")
    }

    @Test
    fun a_file_waiting_to_be_sent_a_refused_one_and_one_waiting_under_a_refused_parent_are_never_touched() = runTest {
        aDayToAttachTo()
        val waiting = aFile("en-attente.jpg")
        val refused = aFile("refusee.jpg")
        val gone = aFile("supprimee-serveur.jpg")
        val synced = aFile("envoyee.jpg")
        db.attachmentDao().upsert(localAttachment("a-waiting", entryLocalId = "e1", localPath = waiting))
        db.attachmentDao().upsert(localAttachment("a-refused", entryLocalId = "e1", localPath = refused).copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.FILE_REFUSED, serverErrorCode = "ATTACHMENT_TOO_LARGE"))
        db.attachmentDao().upsert(localAttachment("a-gone", entryLocalId = "e1", localPath = gone).copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        db.attachmentDao().upsert(localAttachment("a-synced", entryLocalId = "e1", localPath = synced, serverId = 70, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val cleaner = OrphanAttachmentFileCleaner(fileStore, db.attachmentDao(), syncer, backgroundScope)

        assertEquals(emptyList(), cleaner.removeOrphans())

        assertEquals(setOf(waiting, refused, gone, synced), fileStore.storedPaths)
        assertEquals(emptyList(), fileStore.deletedPaths)
        assertEquals(listOf("a-waiting"), db.attachmentDao().findPending().map { it.localId }, "the waiting file is still queued with its bytes")
        assertEquals(4, fileStore.readBytes(waiting).size)
    }

    @Test
    fun the_cleanup_never_runs_in_the_middle_of_a_sync_pass() = runTest {
        aDayToAttachTo()
        aFile("orpheline.jpg")
        val cleaner = OrphanAttachmentFileCleaner(fileStore, db.attachmentDao(), syncer, backgroundScope)

        cleaner.removeOrphans()

        assertEquals(1, syncer.exclusiveCount, "a pass that just saved a file and has not written its row yet finishes first")
    }

    @Test
    fun a_store_that_cannot_be_listed_or_a_file_that_cannot_be_deleted_removes_nothing_else_and_never_crashes_the_start() = runTest {
        aDayToAttachTo()
        val orphan = aFile("orpheline.jpg")
        fileStore.failOnDelete = true
        val cleaner = OrphanAttachmentFileCleaner(fileStore, db.attachmentDao(), syncer, this)

        cleaner.start().join()

        assertEquals(setOf(orphan), fileStore.storedPaths)
        fileStore.failOnDelete = false
        cleaner.start().join()
        assertTrue(fileStore.storedPaths.isEmpty(), "the next start finishes the job")
    }
}
