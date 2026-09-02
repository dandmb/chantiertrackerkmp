package com.dmb.chantiertracker.data.sync

import androidx.work.NetworkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidBackgroundSyncTest {

    @Test
    fun periodic_interval_is_at_least_work_managers_minimum() {
        assertTrue(PERIODIC_SYNC_MINUTES >= 15, "WorkManager clamps anything shorter than 15 min")
    }

    @Test
    fun the_two_work_names_are_distinct_and_non_blank() {
        assertTrue(PERIODIC_SYNC_WORK.isNotBlank())
        assertTrue(EXPEDITED_SYNC_WORK.isNotBlank())
        assertTrue(PERIODIC_SYNC_WORK != EXPEDITED_SYNC_WORK)
    }

    @Test
    fun both_requests_only_run_once_the_device_has_a_network() {
        assertEquals(NetworkType.CONNECTED, syncNetworkConstraints.requiredNetworkType)
    }
}
