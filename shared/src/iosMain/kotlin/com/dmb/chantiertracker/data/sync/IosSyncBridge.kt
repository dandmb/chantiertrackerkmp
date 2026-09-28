package com.dmb.chantiertracker.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/** Must match `BGTaskSchedulerPermittedIdentifiers` in Info.plist and the id registered in iOSApp.swift. */
const val IOS_SYNC_TASK_ID: String = "com.dmb.chantiertracker.sync"

/**
 * Entry point for the `BGAppRefreshTask` handler registered in iOSApp.swift.
 * Queues the next background run, then drains the offline queue once. Returns a
 * cancel lambda for the task's `expirationHandler`; `onFinished(true)` means the
 * pass completed cleanly.
 */
fun startBackgroundSync(onFinished: (Boolean) -> Unit): () -> Unit {
    val koin = KoinPlatform.getKoin()
    koin.get<BackgroundSync>().ensurePeriodicSync()

    val job: Job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        val outcome = koin.get<Syncer>().syncNow()
        onFinished(outcome is SyncOutcome.Synced)
    }
    return { job.cancel() }
}
