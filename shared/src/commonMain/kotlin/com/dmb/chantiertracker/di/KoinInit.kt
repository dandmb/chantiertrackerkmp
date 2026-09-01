package com.dmb.chantiertracker.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) {
    startKoin {
        appDeclaration()
        modules(appModules())
    }
}

// Point d'entrée sans valeur par défaut ni générique, appelable tel quel depuis Swift.
fun doInitKoin() = initKoin()
