package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.core.IosBuildInfo
import com.dmb.chantiertracker.data.local.AppPreferences
import com.dmb.chantiertracker.data.local.IosAppPreferences
import com.dmb.chantiertracker.data.local.IosOnboardingStore
import com.dmb.chantiertracker.data.local.IosTokenStorage
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.projectDatabaseBuilder
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.data.sync.NativeConnectivityObserver
import androidx.room.RoomDatabase
import dev.jordond.connectivity.Connectivity
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<BuildInfo> { IosBuildInfo() }
    single<TokenStorage> { IosTokenStorage() }
    single<OnboardingStore> { IosOnboardingStore() }
    single<AppPreferences> { IosAppPreferences() }
    single<RoomDatabase.Builder<AppDatabase>> { projectDatabaseBuilder() }
    single<ConnectivityObserver> {
        val scope = get<AppCoroutineScope>()
        NativeConnectivityObserver(Connectivity(scope) {}, scope)
    }
}
