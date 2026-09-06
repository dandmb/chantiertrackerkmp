package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dmb.chantiertracker.presentation.navigation.ConsumptionLineFormRoute
import com.dmb.chantiertracker.presentation.navigation.CreateProjectRoute
import com.dmb.chantiertracker.presentation.navigation.CreateStageRoute
import com.dmb.chantiertracker.presentation.navigation.DailyLogRoute
import com.dmb.chantiertracker.presentation.navigation.EditProjectRoute
import com.dmb.chantiertracker.presentation.navigation.EntrySummaryRoute
import com.dmb.chantiertracker.presentation.navigation.InviteMemberRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectDetailRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectsRoute
import com.dmb.chantiertracker.presentation.navigation.PurchaseLineFormRoute
import com.dmb.chantiertracker.presentation.navigation.SettingsRoute
import com.dmb.chantiertracker.presentation.navigation.StageDetailRoute
import com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormScreen
import com.dmb.chantiertracker.presentation.logs.DailyLogScreen
import com.dmb.chantiertracker.presentation.logs.EntrySummaryScreen
import com.dmb.chantiertracker.presentation.logs.PurchaseLineFormScreen
import com.dmb.chantiertracker.presentation.projects.ProjectSortControl
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.edit.EditProjectScreen
import com.dmb.chantiertracker.presentation.projects.invite.InviteMemberScreen
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.presentation.stages.create.CreateStageScreen
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailScreen
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_project_title
import com.dmb.chantiertracker.resources.create_stage_title
import com.dmb.chantiertracker.resources.daily_log_title
import com.dmb.chantiertracker.resources.detail_title
import com.dmb.chantiertracker.resources.edit_project_title
import com.dmb.chantiertracker.resources.invite_member_title
import com.dmb.chantiertracker.resources.projects_new
import com.dmb.chantiertracker.resources.stage_detail_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class MainDestination {
    Projects, Settings, CreateProject, ProjectDetail, EditProject, InviteMember, CreateStage, StageDetail, DailyLog,
    EntrySummary, PurchaseLineForm, ConsumptionLineForm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val account by viewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val current = when {
        destination?.hasRoute(SettingsRoute::class) == true -> MainDestination.Settings
        destination?.hasRoute(CreateProjectRoute::class) == true -> MainDestination.CreateProject
        destination?.hasRoute(ProjectDetailRoute::class) == true -> MainDestination.ProjectDetail
        destination?.hasRoute(EditProjectRoute::class) == true -> MainDestination.EditProject
        destination?.hasRoute(InviteMemberRoute::class) == true -> MainDestination.InviteMember
        destination?.hasRoute(CreateStageRoute::class) == true -> MainDestination.CreateStage
        destination?.hasRoute(StageDetailRoute::class) == true -> MainDestination.StageDetail
        destination?.hasRoute(DailyLogRoute::class) == true -> MainDestination.DailyLog
        destination?.hasRoute(EntrySummaryRoute::class) == true -> MainDestination.EntrySummary
        destination?.hasRoute(PurchaseLineFormRoute::class) == true -> MainDestination.PurchaseLineForm
        destination?.hasRoute(ConsumptionLineFormRoute::class) == true -> MainDestination.ConsumptionLineForm
        else -> MainDestination.Projects
    }
    val currentTab = when (current) {
        MainDestination.Projects -> MainTab.Projects
        MainDestination.Settings -> MainTab.Settings
        else -> null
    }

    var detailTitle by remember { mutableStateOf<String?>(null) }
    var stageTitle by remember { mutableStateOf<String?>(null) }
    var logTitle by remember { mutableStateOf<String?>(null) }
    var formTitle by remember { mutableStateOf<String?>(null) }
    val formDestinations = setOf(
        MainDestination.EntrySummary, MainDestination.PurchaseLineForm, MainDestination.ConsumptionLineForm,
    )
    LaunchedEffect(current) {
        if (current != MainDestination.ProjectDetail) detailTitle = null
        if (current != MainDestination.StageDetail) stageTitle = null
        if (current != MainDestination.DailyLog) logTitle = null
        if (current !in formDestinations) formTitle = null
    }

    Scaffold(
        topBar = {
            when (current) {
                MainDestination.CreateProject -> DetailTopBar(
                    title = stringResource(Res.string.create_project_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.ProjectDetail -> DetailTopBar(
                    title = detailTitle ?: stringResource(Res.string.detail_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.EditProject -> DetailTopBar(
                    title = stringResource(Res.string.edit_project_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.InviteMember -> DetailTopBar(
                    title = stringResource(Res.string.invite_member_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.CreateStage -> DetailTopBar(
                    title = stringResource(Res.string.create_stage_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.StageDetail -> DetailTopBar(
                    title = stageTitle ?: stringResource(Res.string.stage_detail_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.DailyLog -> DetailTopBar(
                    title = logTitle ?: stringResource(Res.string.daily_log_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.EntrySummary, MainDestination.PurchaseLineForm, MainDestination.ConsumptionLineForm -> DetailTopBar(
                    title = formTitle.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
                else -> AppTopBar(
                    userName = account.userName,
                    email = account.email,
                    plan = account.plan,
                    onSubscription = {},
                    onLogout = viewModel::logout,
                    leadingActions = {
                        if (current == MainDestination.Projects) {
                            val sortHolder = koinInject<ProjectSortHolder>()
                            val sort by sortHolder.sort.collectAsStateWithLifecycle()
                            ProjectSortControl(current = sort, onSelect = sortHolder::set)
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                AppBottomBar(
                    current = currentTab,
                    onSelect = { tab ->
                        navController.navigate(tab.route()) {
                            popUpTo(ProjectsRoute) { inclusive = false; saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (currentTab == MainTab.Projects) {
                FloatingActionButton(onClick = { navController.navigate(CreateProjectRoute) }) {
                    Icon(AddIcon, contentDescription = stringResource(Res.string.projects_new))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ProjectsRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<ProjectsRoute> {
                ProjectsScreen(onProjectClick = { localId -> navController.navigate(ProjectDetailRoute(localId)) })
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<CreateProjectRoute> {
                CreateProjectScreen(
                    onCreated = {
                        navController.popBackStack(ProjectsRoute, inclusive = false)
                    },
                )
            }
            composable<ProjectDetailRoute> { entry ->
                ProjectDetailScreen(
                    projectLocalId = entry.toRoute<ProjectDetailRoute>().projectLocalId,
                    onProjectNameResolved = { detailTitle = it },
                    onAddStage = { projectLocalId -> navController.navigate(CreateStageRoute(projectLocalId)) },
                    onStageClick = { stageLocalId -> navController.navigate(StageDetailRoute(stageLocalId)) },
                    onEditProject = { projectLocalId -> navController.navigate(EditProjectRoute(projectLocalId)) },
                    onInviteMember = { projectLocalId -> navController.navigate(InviteMemberRoute(projectLocalId)) },
                    onProjectDeleted = { navController.popBackStack(ProjectsRoute, inclusive = false) },
                )
            }
            composable<InviteMemberRoute> { entry ->
                InviteMemberScreen(
                    projectLocalId = entry.toRoute<InviteMemberRoute>().projectLocalId,
                    onInvited = { navController.popBackStack() },
                )
            }
            composable<EditProjectRoute> { entry ->
                EditProjectScreen(
                    projectLocalId = entry.toRoute<EditProjectRoute>().projectLocalId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<CreateStageRoute> { entry ->
                CreateStageScreen(
                    projectLocalId = entry.toRoute<CreateStageRoute>().projectLocalId,
                    onCreated = { navController.popBackStack() },
                )
            }
            composable<StageDetailRoute> { entry ->
                StageDetailScreen(
                    stageLocalId = entry.toRoute<StageDetailRoute>().stageLocalId,
                    onStageNameResolved = { stageTitle = it },
                    onOpenLog = { dailyLogLocalId -> navController.navigate(DailyLogRoute(dailyLogLocalId)) },
                )
            }
            composable<DailyLogRoute> { entry ->
                DailyLogScreen(
                    dailyLogLocalId = entry.toRoute<DailyLogRoute>().dailyLogLocalId,
                    onDateResolved = { logTitle = it },
                    onEditEntry = { entryLocalId -> navController.navigate(EntrySummaryRoute(entryLocalId)) },
                    onAddPurchaseLine = { entryLocalId, projectLocalId, currency ->
                        navController.navigate(PurchaseLineFormRoute(entryLocalId, projectLocalId, null, currency))
                    },
                    onEditPurchaseLine = { entryLocalId, projectLocalId, lineLocalId, currency ->
                        navController.navigate(PurchaseLineFormRoute(entryLocalId, projectLocalId, lineLocalId, currency))
                    },
                    onAddConsumptionLine = { entryLocalId, projectLocalId ->
                        navController.navigate(ConsumptionLineFormRoute(entryLocalId, projectLocalId))
                    },
                    onEditConsumptionLine = { entryLocalId, projectLocalId, lineLocalId ->
                        navController.navigate(ConsumptionLineFormRoute(entryLocalId, projectLocalId, lineLocalId))
                    },
                )
            }
            composable<EntrySummaryRoute> { entry ->
                EntrySummaryScreen(
                    entryLocalId = entry.toRoute<EntrySummaryRoute>().entryLocalId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTitleResolved = { formTitle = it },
                )
            }
            composable<PurchaseLineFormRoute> { entry ->
                val route = entry.toRoute<PurchaseLineFormRoute>()
                PurchaseLineFormScreen(
                    entryLocalId = route.entryLocalId,
                    projectLocalId = route.projectLocalId,
                    lineLocalId = route.lineLocalId,
                    currency = route.currency,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTitleResolved = { formTitle = it },
                )
            }
            composable<ConsumptionLineFormRoute> { entry ->
                val route = entry.toRoute<ConsumptionLineFormRoute>()
                ConsumptionLineFormScreen(
                    entryLocalId = route.entryLocalId,
                    projectLocalId = route.projectLocalId,
                    lineLocalId = route.lineLocalId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTitleResolved = { formTitle = it },
                )
            }
        }
    }
}
