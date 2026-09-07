package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.core.DesktopBuildInfo
import com.dmb.chantiertracker.data.local.AppPreferences
import com.dmb.chantiertracker.data.local.DesktopAppPreferences
import com.dmb.chantiertracker.data.local.DesktopOnboardingStore
import com.dmb.chantiertracker.data.local.DesktopTokenStorage
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.projectDatabaseBuilder
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.data.sync.DesktopConnectivityObserver
import androidx.room.RoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<BuildInfo> { DesktopBuildInfo() }
    single<TokenStorage> { DesktopTokenStorage() }
    single<OnboardingStore> { DesktopOnboardingStore() }
    single<AppPreferences> { DesktopAppPreferences() }
    single<RoomDatabase.Builder<AppDatabase>> { projectDatabaseBuilder() }
    single<ConnectivityObserver> { DesktopConnectivityObserver(get<AppCoroutineScope>()) }
}
