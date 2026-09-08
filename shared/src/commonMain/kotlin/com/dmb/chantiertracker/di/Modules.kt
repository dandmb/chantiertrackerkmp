package com.dmb.chantiertracker.di

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.FileKitAttachmentFileStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.InvitationDao
import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.DailyLogDao
import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.PlanUsageDao
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import androidx.room.RoomDatabase
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.ConsumptionLineApi
import com.dmb.chantiertracker.data.remote.DailyLogApi
import com.dmb.chantiertracker.data.remote.HistoryApi
import com.dmb.chantiertracker.data.remote.MaterialApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.PurchaseLineApi
import com.dmb.chantiertracker.data.remote.ReportApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.backgroundSyncModule
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.data.repository.AccountRepositoryImpl
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.repository.InvitationRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.repository.ConsumptionLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.repository.HistoryRepositoryImpl
import com.dmb.chantiertracker.data.repository.MaterialRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.PurchaseLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.ReportRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AttachmentRepository
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ConsumptionLineRepository
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import com.dmb.chantiertracker.domain.repository.HistoryRepository
import com.dmb.chantiertracker.domain.repository.MaterialRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.PurchaseLineRepository
import com.dmb.chantiertracker.domain.repository.ReportRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormViewModel
import com.dmb.chantiertracker.presentation.logs.DailyLogViewModel
import com.dmb.chantiertracker.presentation.logs.EntrySummaryViewModel
import com.dmb.chantiertracker.presentation.logs.PurchaseLineFormViewModel
import com.dmb.chantiertracker.presentation.main.MainViewModel
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectViewModel
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailViewModel
import com.dmb.chantiertracker.presentation.projects.edit.EditProjectViewModel
import com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryViewModel
import com.dmb.chantiertracker.presentation.projects.invite.InviteMemberViewModel
import com.dmb.chantiertracker.presentation.reports.ProjectReportsViewModel
import com.dmb.chantiertracker.presentation.reports.ReportEntryViewModel
import com.dmb.chantiertracker.presentation.stages.create.CreateStageViewModel
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailViewModel
import com.dmb.chantiertracker.presentation.settings.AppSettings
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
    singleOf(::MaterialApi)
    singleOf(::DailyLogApi)
    singleOf(::PurchaseLineApi)
    singleOf(::ConsumptionLineApi)
    singleOf(::AttachmentApi)
    singleOf(::InvitationApi)
    singleOf(::HistoryApi)
    singleOf(::ReportApi)
}

val syncModule: Module = module {
    single<AppDatabase> { get<RoomDatabase.Builder<AppDatabase>>().buildChantierDatabase() }
    single<ProjectDao> { get<AppDatabase>().projectDao() }
    single<StageDao> { get<AppDatabase>().stageDao() }
    single<PlanUsageDao> { get<AppDatabase>().planUsageDao() }
    single<DailyLogDao> { get<AppDatabase>().dailyLogDao() }
    single<DailyEntryDao> { get<AppDatabase>().dailyEntryDao() }
    single<MaterialDao> { get<AppDatabase>().materialDao() }
    single<PurchaseLineDao> { get<AppDatabase>().purchaseLineDao() }
    single<ConsumptionLineDao> { get<AppDatabase>().consumptionLineDao() }
    single<AttachmentDao> { get<AppDatabase>().attachmentDao() }
    single<InvitationDao> { get<AppDatabase>().invitationDao() }
    single<AttachmentFileStore> { FileKitAttachmentFileStore(newFileName = { kotlin.uuid.Uuid.random().toString() }) }
    single { AppCoroutineScope() }
    single { SyncStateHolder() }
    single {
        SyncEngine(
            dao = get(),
            api = get(),
            stageDao = get(),
            stageApi = get(),
            materialDao = get(),
            materialApi = get(),
            dailyLogDao = get(),
            dailyEntryDao = get(),
            dailyLogApi = get(),
            purchaseLineDao = get(),
            purchaseLineApi = get(),
            consumptionLineDao = get(),
            consumptionLineApi = get(),
            attachmentDao = get(),
            attachmentApi = get(),
            attachmentFileStore = get(),
            invitationDao = get(),
            invitationApi = get(),
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
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<DailyLogRepository> { DailyLogRepositoryImpl(get(), get(), get(), get<AppCoroutineScope>()) }
    single<MaterialRepository> { MaterialRepositoryImpl(get(), get(), get(), get(), get<AppCoroutineScope>()) }
    single<PurchaseLineRepository> { PurchaseLineRepositoryImpl(get(), get(), get<AppCoroutineScope>()) }
    single<ConsumptionLineRepository> { ConsumptionLineRepositoryImpl(get(), get(), get<AppCoroutineScope>()) }
    single<AttachmentRepository> { AttachmentRepositoryImpl(get(), get(), get(), get(), get(), get<AppCoroutineScope>()) }
    single<InvitationRepository> { InvitationRepositoryImpl(get(), get(), get(), get()) }
    single<HistoryRepository> { HistoryRepositoryImpl(get(), get()) }
    single<ReportRepository> { ReportRepositoryImpl(get(), get(), get()) }
}

val presentationModule: Module = module {
    single { ProjectSortHolder() }
    single { AppSettings(get(), get<AppCoroutineScope>()) }
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
    viewModelOf(::EditProjectViewModel)
    viewModelOf(::ProjectHistoryViewModel)
    viewModelOf(::InviteMemberViewModel)
    viewModelOf(::ReportEntryViewModel)
    viewModelOf(::ProjectReportsViewModel)
    viewModelOf(::CreateStageViewModel)
    viewModelOf(::StageDetailViewModel)
    viewModelOf(::DailyLogViewModel)
    viewModelOf(::EntrySummaryViewModel)
    viewModelOf(::PurchaseLineFormViewModel)
    viewModelOf(::ConsumptionLineFormViewModel)
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
