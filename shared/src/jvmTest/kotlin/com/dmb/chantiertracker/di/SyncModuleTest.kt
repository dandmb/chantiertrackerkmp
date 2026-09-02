package com.dmb.chantiertracker.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakeTokenStorage
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class SyncModuleTest {

    private val fakePlatformModule = module {
        single<BuildInfo> { FakeBuildInfo(isDebug = true) }
        single<TokenStorage> { FakeTokenStorage() }
        single<OnboardingStore> { FakeOnboardingStore() }
        single<RoomDatabase.Builder<AppDatabase>> { Room.inMemoryDatabaseBuilder<AppDatabase>() }
        single<ConnectivityObserver> { FakeConnectivityObserver() }
    }

    @Test
    fun sync_module_exposes_a_single_database_and_dao() {
        val koin = koinApplication {
            modules(fakePlatformModule, networkModule, syncModule, dataModule, presentationModule)
        }.koin

        val db = koin.get<AppDatabase>()
        assertNotNull(db)
        assertSame(db, koin.get<AppDatabase>())
        assertSame(db.projectDao(), koin.get<ProjectDao>())
        assertNotNull(koin.get<AppConfig>())
        assertNotNull(koin.get<SyncEngine>())
        assertSame(koin.get<SyncEngine>(), koin.get<SyncEngine>())

        db.close()
        koin.close()
    }

    @Test
    fun app_modules_count_includes_sync_module() {
        assertEquals(5, appModules().size)
    }
}
