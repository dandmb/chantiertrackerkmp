package com.dmb.chantiertracker.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs one catch-up sync pass. WorkManager only starts it once its
 * `NetworkType.CONNECTED` constraint is met, so a clean pass ends in success;
 * a transient failure asks WorkManager to retry with its own backoff.
 * Resolves [Syncer] from the running Koin graph — [com.dmb.chantiertracker.di.initKoin]
 * has already run in `Application.onCreate` by the time any worker executes.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val syncer: Syncer by inject()

    override suspend fun doWork(): Result = when (syncer.syncNow()) {
        is SyncOutcome.Synced -> Result.success()
        is SyncOutcome.Skipped -> Result.success()
        is SyncOutcome.Failed -> Result.retry()
    }
}
