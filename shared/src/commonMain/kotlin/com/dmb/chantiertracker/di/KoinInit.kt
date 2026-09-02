package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.data.sync.BackgroundSync
import com.dmb.chantiertracker.data.sync.SyncEngine
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) {
    val koin = startKoin {
        appDeclaration()
        modules(appModules())
    }.koin
    // Begin watching connectivity and draining the offline queue for the app's lifetime.
    koin.get<SyncEngine>().start()
    // Register the OS-level catch-up job (WorkManager / BGTaskScheduler; no-op on Desktop).
    koin.get<BackgroundSync>().ensurePeriodicSync()
}

// Point d'entrée sans valeur par défaut ni générique, appelable tel quel depuis Swift.
fun doInitKoin() = initKoin()
