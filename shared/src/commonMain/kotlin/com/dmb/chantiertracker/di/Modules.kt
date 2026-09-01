package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.data.remote.ApiService
import com.dmb.chantiertracker.data.remote.AuthTokenProvider
import com.dmb.chantiertracker.data.remote.NoAuthTokenProvider
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.AppInfoRepositoryImpl
import com.dmb.chantiertracker.domain.repository.AppInfoRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import com.dmb.chantiertracker.presentation.hello.HelloViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule: Module = module {
    single<AuthTokenProvider> { NoAuthTokenProvider() }
    single<HttpClient> {
        createHttpClient(
            engine = httpClientEngine(),
            tokenProvider = get(),
        )
    }
    singleOf(::ApiService)
}

val dataModule: Module = module {
    singleOf(::AppInfoRepositoryImpl) bind AppInfoRepository::class
}

val domainModule: Module = module {
    factoryOf(::GetWelcomeMessageUseCase)
}

val presentationModule: Module = module {
    viewModelOf(::HelloViewModel)
}

fun appModules(): List<Module> = listOf(
    networkModule,
    dataModule,
    domainModule,
    presentationModule,
)
