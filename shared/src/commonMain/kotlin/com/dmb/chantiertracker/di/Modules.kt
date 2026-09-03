package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import androidx.room.RoomDatabase
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.backgroundSyncModule
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.data.repository.AccountRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.presentation.main.MainViewModel
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectViewModel
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailViewModel
import com.dmb.chantiertracker.presentation.stages.create.CreateStageViewModel
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailViewModel
import com.dmb.chantiertracker.presentation.settings.SettingsViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
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
    singleOf(::ProjectApi)
    singleOf(::StageApi)
    singleOf(::AccountApi)
}

val syncModule: Module = module {
    single<AppDatabase> { get<RoomDatabase.Builder<AppDatabase>>().buildChantierDatabase() }
    single<ProjectDao> { get<AppDatabase>().projectDao() }
    single<StageDao> { get<AppDatabase>().stageDao() }
    single { AppCoroutineScope() }
    single { SyncStateHolder() }
    single {
        SyncEngine(
            dao = get(),
            api = get(),
            stageDao = get(),
            stageApi = get(),
            connectivity = get(),
            syncState = get(),
            scope = get<AppCoroutineScope>(),
            backgroundSync = get(),
        )
    }
    single<Syncer> { get<SyncEngine>() }
}

val dataModule: Module = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get<TokenStorage>(), get(), get()) }
    single<ProjectRepository> { ProjectRepositoryImpl(get(), get(), get<AppCoroutineScope>()) }
    single<StageRepository> { StageRepositoryImpl(get(), get(), get<AppCoroutineScope>()) }
    single<AccountRepository> { AccountRepositoryImpl(get()) }
}

val presentationModule: Module = module {
    single { ProjectSortHolder() }
    viewModelOf(::RootViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::VerifyEmailViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ResetPasswordViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::ProjectsViewModel)
    viewModelOf(::CreateProjectViewModel)
    viewModelOf(::ProjectDetailViewModel)
    viewModelOf(::CreateStageViewModel)
    viewModelOf(::StageDetailViewModel)
    viewModelOf(::SettingsViewModel)
}

fun appModules(): List<Module> = listOf(
    platformModule(),
    networkModule,
    syncModule,
    backgroundSyncModule(),
    dataModule,
    presentationModule,
)
