@file:OptIn(ExperimentalForeignApi::class)

package com.dmb.chantiertracker.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

private const val PERIODIC_DELAY_SECONDS = 15.0 * 60.0
private const val EXPEDITED_DELAY_SECONDS = 60.0

class IosBackgroundSync : BackgroundSync {

    override fun ensurePeriodicSync() = submitRefreshRequest(PERIODIC_DELAY_SECONDS)

    override fun requestExpeditedSync() = submitRefreshRequest(EXPEDITED_DELAY_SECONDS)

    private fun submitRefreshRequest(minDelaySeconds: Double) {
        val request = BGAppRefreshTaskRequest(IOS_SYNC_TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(minDelaySeconds)
        }
        // Throws if the identifier isn't declared in Info.plist, or on the simulator.
        runCatching { BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null) }
    }
}

actual fun backgroundSyncModule(): Module = module {
    single<BackgroundSync> { IosBackgroundSync() }
}
