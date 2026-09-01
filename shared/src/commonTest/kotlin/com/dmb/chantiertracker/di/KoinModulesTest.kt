package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.data.remote.ApiService
import com.dmb.chantiertracker.data.remote.AuthTokenProvider
import com.dmb.chantiertracker.domain.repository.AppInfoRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import io.ktor.client.HttpClient
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KoinModulesTest {

    @AfterTest
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun graph_resolves_core_dependencies() {
        val koin = koinApplication { modules(appModules()) }.koin

        assertNotNull(koin.get<AuthTokenProvider>())
        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<ApiService>())
        assertNotNull(koin.get<AppInfoRepository>())
        assertNotNull(koin.get<GetWelcomeMessageUseCase>())

        koin.close()
    }

    @Test
    fun exposes_four_layer_modules() {
        assertTrue(appModules().size == 4)
    }
}
