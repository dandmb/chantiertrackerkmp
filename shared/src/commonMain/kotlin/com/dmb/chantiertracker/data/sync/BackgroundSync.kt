package com.dmb.chantiertracker.data.sync

import org.koin.core.module.Module

/**
 * Hands the platform's OS scheduler a way to run a catch-up sync when the app
 * is not in the foreground: WorkManager on Android, `BGTaskScheduler` on iOS.
 * Desktop keeps the process alive, so [SyncEngine]'s own periodic loop is
 * enough and the actual is a no-op (ADR-22).
 */
interface BackgroundSync {
    /** Register the recurring catch-up job. Idempotent — safe to call on every launch. */
    fun ensurePeriodicSync()

    /** Ask the OS to run a catch-up sync as soon as its constraints allow, e.g. right after an offline write. */
    fun requestExpeditedSync()
}

object NoOpBackgroundSync : BackgroundSync {
    override fun ensurePeriodicSync() = Unit
    override fun requestExpeditedSync() = Unit
}

expect fun backgroundSyncModule(): Module
