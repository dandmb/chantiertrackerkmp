package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.AppInfoRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AppInfoRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.presentation.home.HomeViewModel
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule: Module = module {
    single { AppConfig(get()) }
    single { AuthStateHolder() }
    single<HttpClient> {
        val appConfig = get<AppConfig>()
        val authStateHolder = get<AuthStateHolder>()
        createHttpClient(
            engine = httpClientEngine(),
            tokenStorage = get(),
            baseUrl = appConfig.baseUrl,
            enableLogging = appConfig.enableNetworkLogging,
            onSessionExpired = { authStateHolder.update(AuthState.Unauthenticated) },
        )
    }
    singleOf(::AuthApi)
}

val dataModule: Module = module {
    single<AppInfoRepository> { AppInfoRepositoryImpl(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get<TokenStorage>(), get(), get()) }
}

val domainModule: Module = module {
    factoryOf(::GetWelcomeMessageUseCase)
}

val presentationModule: Module = module {
    viewModelOf(::RootViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::VerifyEmailViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ResetPasswordViewModel)
    viewModelOf(::HomeViewModel)
}

fun appModules(): List<Module> = listOf(
    platformModule(),
    networkModule,
    dataModule,
    domainModule,
    presentationModule,
)
