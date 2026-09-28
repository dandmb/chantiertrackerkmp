package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.local.db.PlanUsageDao
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakePlanUsageDao
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeStageDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeTokenStorage
import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KoinModulesTest {

    private val fakePlatformModule = module {
        single<BuildInfo> { FakeBuildInfo(isDebug = true) }
        single<TokenStorage> { FakeTokenStorage() }
        single<OnboardingStore> { FakeOnboardingStore() }
    }

    // Stands in for syncModule without a real Room DB (unavailable on the Android host test JVM).
    private val fakeSyncModule = module {
        single { AppCoroutineScope() }
        single<ProjectDao> { FakeProjectDao() }
        single<StageDao> { FakeStageDao() }
        single<PlanUsageDao> { FakePlanUsageDao() }
        single<Syncer> { FakeSyncer() }
    }

    @Test
    fun graph_resolves_all_shared_dependencies() {
        val koin = koinApplication {
            modules(fakePlatformModule, fakeSyncModule, networkModule, dataModule, presentationModule)
        }.koin

        assertNotNull(koin.get<AppConfig>())
        assertNotNull(koin.get<AuthStateHolder>())
        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<AuthApi>())
        assertNotNull(koin.get<ProjectApi>())
        assertNotNull(koin.get<StageApi>())
        assertNotNull(koin.get<AccountApi>())
        assertNotNull(koin.get<AuthRepository>())
        assertNotNull(koin.get<ProjectRepository>())
        assertNotNull(koin.get<StageRepository>())
        assertNotNull(koin.get<AccountRepository>())

        koin.close()
    }

    @Test
    fun app_modules_include_platform_and_background_sync_modules() {
        assertEquals(6, appModules().size)
    }
}
