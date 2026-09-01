package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.domain.repository.AppInfoRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakeTokenStorage
import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KoinModulesTest {

    private val fakePlatformModule = module {
        single<BuildInfo> { FakeBuildInfo(isDebug = true) }
        single<TokenStorage> { FakeTokenStorage() }
        single<OnboardingStore> { FakeOnboardingStore() }
    }

    @Test
    fun graph_resolves_all_shared_dependencies() {
        val koin = koinApplication {
            modules(fakePlatformModule, networkModule, dataModule, domainModule, presentationModule)
        }.koin

        assertNotNull(koin.get<AppConfig>())
        assertNotNull(koin.get<AuthStateHolder>())
        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<AuthApi>())
        assertNotNull(koin.get<AppInfoRepository>())
        assertNotNull(koin.get<AuthRepository>())
        assertNotNull(koin.get<GetWelcomeMessageUseCase>())

        koin.close()
    }

    @Test
    fun app_modules_include_platform_module() {
        assertTrue(appModules().size == 5)
    }
}
