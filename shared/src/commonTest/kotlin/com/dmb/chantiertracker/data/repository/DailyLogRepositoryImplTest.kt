package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.support.FakeDailyEntryDao
import com.dmb.chantiertracker.support.FakeDailyLogDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyLogRepositoryImplTest {

    private fun repo(
        logDao: FakeDailyLogDao = FakeDailyLogDao(),
        entryDao: FakeDailyEntryDao = FakeDailyEntryDao(logsByLocalId = { logDao.stored.associate { it.localId to it.stageLocalId } }),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = DailyLogRepositoryImpl(logDao, entryDao, syncer, AppCoroutineScope(), clock, newLocalId = idSequence())

    private fun idSequence(): () -> String {
        var n = 0
        return { "fixed-id-${n++}" }
    }

    @Test
    fun observe_logs_computes_has_purchase_and_has_work_from_entries() = runTest {
        val logDao = FakeDailyLogDao(listOf(localDailyLog("log-1", stageLocalId = "s1", date = "2026-09-05")))
        val entryDao = FakeDailyEntryDao(
            listOf(
                localDailyEntry("e1", dailyLogLocalId = "log-1", type = "PURCHASE"),
                localDailyEntry("e2", dailyLogLocalId = "log-1", type = "WORK", pendingOp = PendingOp.DELETE),
            ),
            logsByLocalId = { logDao.stored.associate { it.localId to it.stageLocalId } },
        )

        val logs = repo(logDao, entryDao).observeLogs("s1").first()

        assertEquals(1, logs.size)
        assertTrue(logs.single().hasPurchase)
        assertTrue(!logs.single().hasWork, "a pending-delete entry is not counted")
    }

    @Test
    fun observe_log_maps_the_day_and_its_entries() = runTest {
        val logDao = FakeDailyLogDao(listOf(localDailyLog("log-1", stageLocalId = "s1", date = "2026-09-05")))
        val entryDao = FakeDailyEntryDao(
            listOf(localDailyEntry("e1", dailyLogLocalId = "log-1", type = "PURCHASE", summary = "Ciment")),
            logsByLocalId = { logDao.stored.associate { it.localId to it.stageLocalId } },
        )

        val detail = repo(logDao, entryDao).observeLog("log-1").first()

        assertEquals("2026-09-05", detail?.date)
        assertEquals("s1", detail?.stageLocalId)
        assertEquals(listOf(EntryType.PURCHASE), detail?.entries?.map { it.type })
        assertEquals("Ciment", detail?.entries?.single()?.summary)
        assertNull(repo().observeLog("missing").first())
    }

    @Test
    fun create_purchase_entry_creates_the_day_and_a_pending_entry() = runTest {
        val logDao = FakeDailyLogDao()
        val syncer = FakeSyncer()
        val r = repo(logDao, syncer = syncer, clock = MutableClock(4_242L))

        val logLocalId = r.createPurchaseEntry("s1", "2026-09-05")

        val log = logDao.findByLocalId(logLocalId)!!
        assertEquals("s1", log.stageLocalId)
        assertEquals("2026-09-05", log.date)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun creating_the_same_entry_type_twice_is_idempotent() = runTest {
        val logDao = FakeDailyLogDao()
        val entryDao = FakeDailyEntryDao(logsByLocalId = { logDao.stored.associate { it.localId to it.stageLocalId } })
        val syncer = FakeSyncer()
        val r = repo(logDao, entryDao, syncer)

        val first = r.createPurchaseEntry("s1", "2026-09-05")
        val second = r.createPurchaseEntry("s1", "2026-09-05")

        assertEquals(first, second, "the same day is reused, not duplicated")
        assertEquals(1, entryDao.stored.size, "no duplicate PURCHASE entry created")
        assertEquals(1, syncer.requestCount, "the second call is a no-op — no sync nudge")
    }

    @Test
    fun create_work_entry_on_an_existing_day_reuses_it() = runTest {
        val logDao = FakeDailyLogDao()
        val entryDao = FakeDailyEntryDao(logsByLocalId = { logDao.stored.associate { it.localId to it.stageLocalId } })
        val r = repo(logDao, entryDao)

        val purchaseDayId = r.createPurchaseEntry("s1", "2026-09-05")
        val workDayId = r.createWorkEntry("s1", "2026-09-05")

        assertEquals(purchaseDayId, workDayId, "both entry types land on the same day")
        assertEquals(1, logDao.stored.size)
        assertEquals(setOf("PURCHASE", "WORK"), entryDao.stored.map { it.type }.toSet())
    }

    @Test
    fun update_entry_writes_a_pending_update_and_trims_blank_to_null() = runTest {
        val entryDao = FakeDailyEntryDao(
            listOf(localDailyEntry("e1", type = "WORK", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)),
        )
        val syncer = FakeSyncer()

        repo(entryDao = entryDao, syncer = syncer, clock = MutableClock(5_555L)).updateEntry("e1", "   ")

        val row = entryDao.findByLocalId("e1")!!
        assertNull(row.summary)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.UPDATE, row.pendingOp)
        assertEquals(5_555L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun update_entry_on_a_row_that_never_synced_keeps_it_a_create() = runTest {
        val entryDao = FakeDailyEntryDao(listOf(localDailyEntry("e1", pendingOp = PendingOp.CREATE)))

        repo(entryDao = entryDao).updateEntry("e1", "Fondations coulées")

        val row = entryDao.findByLocalId("e1")!!
        assertEquals(PendingOp.CREATE, row.pendingOp)
        assertEquals("Fondations coulées", row.summary)
    }

    @Test
    fun update_entry_ignores_an_unknown_local_id() = runTest {
        val syncer = FakeSyncer()
        repo(syncer = syncer).updateEntry("missing", "x")
        assertEquals(0, syncer.requestCount)
    }

    @Test
    fun refresh_logs_and_refresh_log_delegate_to_a_full_sync_pass() = runTest {
        val syncer = FakeSyncer()
        val r = repo(syncer = syncer)

        r.refreshLogs("s1")
        r.refreshLog("log-1")

        assertEquals(2, syncer.syncCount)
    }
}
