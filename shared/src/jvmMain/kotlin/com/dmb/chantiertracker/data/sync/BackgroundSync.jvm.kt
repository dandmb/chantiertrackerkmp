package com.dmb.chantiertracker.data.sync

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop has no OS background scheduler and the process stays alive, so
 * [SyncEngine]'s own connectivity watcher + periodic catch-up loop cover it
 * entirely (ADR-22). Nothing to schedule here.
 */
actual fun backgroundSyncModule(): Module = module {
    single<BackgroundSync> { NoOpBackgroundSync }
}
