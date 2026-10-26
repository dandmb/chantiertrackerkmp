package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.CreatedDailyEntry
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyLogRepositoryOnRoomTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), FakeSyncer(), AppCoroutineScope())

    @AfterTest fun close() = db.close()

    private suspend fun aStage() {
        db.projectDao().upsert(localProject("p", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        db.stageDao().upsert(localStage("s", projectLocalId = "p", serverId = 10, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    }

    @Test
    fun on_a_real_database_each_creation_returns_the_day_and_its_own_entry() = runTest {
        aStage()

        val purchase = logs.createPurchaseEntry("s", "2026-09-05")
        val work = logs.createWorkEntry("s", "2026-09-05")
        val nextDay = logs.createPurchaseEntry("s", "2026-09-06")
        val again = logs.createPurchaseEntry("s", "2026-09-05")

        assertEquals(purchase.dailyLogLocalId, work.dailyLogLocalId, "one day for both entries")
        assertEquals(4, setOf(purchase.dailyLogLocalId, nextDay.dailyLogLocalId, purchase.entryLocalId, work.entryLocalId, nextDay.entryLocalId).size - 1, "five distinct ids")
        assertEquals(purchase, again, "asking again returns the same day and the same entry")
        assertEquals(
            listOf(EntryType.PURCHASE to purchase.dailyLogLocalId, EntryType.WORK to work.dailyLogLocalId, EntryType.PURCHASE to nextDay.dailyLogLocalId),
            listOf(purchase, work, nextDay).map { created -> logs.observeEntry(created.entryLocalId).first()!!.let { it.type to it.dailyLogLocalId } },
            "each returned entry id opens an entry of the asked type on the returned day",
        )
        assertEquals(
            listOf(purchase.entryLocalId, work.entryLocalId).sorted(),
            logs.observeLog(purchase.dailyLogLocalId).first()!!.entries.map { it.localId }.sorted(),
        )
    }

    @Test
    fun current_behaviour_not_a_rule_on_a_real_database_recreating_an_entry_that_waits_for_its_deletion_creates_nothing_and_returns_an_id_of_no_entry() = runTest {
        aStage()
        db.dailyLogDao().upsert(localDailyLog("l", stageLocalId = "s", date = "2026-09-05", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e-deleting", dailyLogLocalId = "l", type = "PURCHASE", serverId = 30, pendingOp = PendingOp.DELETE))

        val created = logs.createPurchaseEntry("s", "2026-09-05")

        assertEquals("l", created.dailyLogLocalId)
        assertTrue(created.entryLocalId != "e-deleting")
        assertNull(db.dailyEntryDao().findByLocalId(created.entryLocalId), "the unique (day, type) index refuses the second row and Room's upsert writes nothing: the returned entry does not exist")
        assertEquals(listOf("e-deleting"), db.dailyEntryDao().findForLog("l").map { it.localId }, "the entry waiting for its deletion is untouched")
        assertNull(logs.observeEntry(created.entryLocalId).first(), "no screen deletes an entry today, so nothing reaches this path")
    }
}
