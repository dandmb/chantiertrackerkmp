package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.session.RoomPendingWriteCounter
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingWritesCountTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val counter = RoomPendingWriteCounter(db.localDataDao())

    @AfterTest fun close() = db.close()

    @Test
    fun only_writes_waiting_to_be_sent_are_counted_not_the_synced_nor_the_refused_ones() = runTest {
        db.projectDao().upsert(localProject("p-synced", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.projectDao().upsert(localProject("p-new"))
        db.projectDao().upsert(localProject("p-refused", syncStatus = SyncStatus.CONFLICTED))
        db.stageDao().upsert(localStage("s-new", projectLocalId = "p-new"))

        assertEquals(2, counter.observePending().first())

        db.stageDao().upsert(localStage("s-new", projectLocalId = "p-new", syncStatus = SyncStatus.SYNCED))

        assertEquals(1, counter.observePending().first())
    }
}
