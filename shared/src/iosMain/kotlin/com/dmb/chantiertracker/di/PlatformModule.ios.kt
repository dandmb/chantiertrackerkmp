package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.core.IosBuildInfo
import com.dmb.chantiertracker.data.local.IosOnboardingStore
import com.dmb.chantiertracker.data.local.IosTokenStorage
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<BuildInfo> { IosBuildInfo() }
    single<TokenStorage> { IosTokenStorage() }
    single<OnboardingStore> { IosOnboardingStore() }
}
