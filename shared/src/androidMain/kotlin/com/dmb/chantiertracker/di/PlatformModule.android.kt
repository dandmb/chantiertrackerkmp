package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.AndroidBuildInfo
import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.local.AndroidOnboardingStore
import com.dmb.chantiertracker.data.local.AndroidTokenStorage
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<BuildInfo> { AndroidBuildInfo(androidContext()) }
    single<TokenStorage> { AndroidTokenStorage(androidContext()) }
    single<OnboardingStore> { AndroidOnboardingStore(androidContext()) }
}
