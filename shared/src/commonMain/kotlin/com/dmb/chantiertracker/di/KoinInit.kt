package com.dmb.chantiertracker.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(appModules())
    }
}

// Point d'entrée sans valeur par défaut ni générique, appelable tel quel depuis Swift.
fun doInitKoin() = initKoin()
