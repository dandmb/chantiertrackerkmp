package com.dmb.chantiertracker.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** WorkManager's own floor for a periodic request; keeping our value here makes the intent explicit. */
internal const val PERIODIC_SYNC_MINUTES = 15L
internal const val PERIODIC_SYNC_WORK = "chantiertracker-sync-periodic"
internal const val EXPEDITED_SYNC_WORK = "chantiertracker-sync-oneshot"

internal val syncNetworkConstraints: Constraints =
    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

class AndroidBackgroundSync(private val context: Context) : BackgroundSync {

    private val workManager get() = WorkManager.getInstance(context)

    override fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_SYNC_MINUTES, TimeUnit.MINUTES)
            .setConstraints(syncNetworkConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun requestExpeditedSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syncNetworkConstraints)
            .build()
        workManager.enqueueUniqueWork(EXPEDITED_SYNC_WORK, ExistingWorkPolicy.KEEP, request)
    }
}

actual fun backgroundSyncModule(): Module = module {
    single<BackgroundSync> { AndroidBackgroundSync(androidContext()) }
}
