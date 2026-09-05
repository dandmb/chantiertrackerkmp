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
import com.dmb.chantiertracker.presentation.navigation.CreateProjectRoute
import com.dmb.chantiertracker.presentation.navigation.CreateStageRoute
import com.dmb.chantiertracker.presentation.navigation.EditProjectRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectDetailRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectsRoute
import com.dmb.chantiertracker.presentation.navigation.SettingsRoute
import com.dmb.chantiertracker.presentation.navigation.StageDetailRoute
import com.dmb.chantiertracker.presentation.projects.ProjectSortControl
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.edit.EditProjectScreen
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.presentation.stages.create.CreateStageScreen
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailScreen
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_project_title
import com.dmb.chantiertracker.resources.create_stage_title
import com.dmb.chantiertracker.resources.detail_title
import com.dmb.chantiertracker.resources.edit_project_title
import com.dmb.chantiertracker.resources.projects_new
import com.dmb.chantiertracker.resources.stage_detail_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class MainDestination { Projects, Settings, CreateProject, ProjectDetail, EditProject, CreateStage, StageDetail }

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
        destination?.hasRoute(CreateStageRoute::class) == true -> MainDestination.CreateStage
        destination?.hasRoute(StageDetailRoute::class) == true -> MainDestination.StageDetail
        else -> MainDestination.Projects
    }
    val currentTab = when (current) {
        MainDestination.Projects -> MainTab.Projects
        MainDestination.Settings -> MainTab.Settings
        else -> null
    }

    var detailTitle by remember { mutableStateOf<String?>(null) }
    var stageTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        if (current != MainDestination.ProjectDetail) detailTitle = null
        if (current != MainDestination.StageDetail) stageTitle = null
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
                MainDestination.CreateStage -> DetailTopBar(
                    title = stringResource(Res.string.create_stage_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.StageDetail -> DetailTopBar(
                    title = stageTitle ?: stringResource(Res.string.stage_detail_title),
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
                    onProjectDeleted = { navController.popBackStack(ProjectsRoute, inclusive = false) },
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
                )
            }
        }
    }
}
