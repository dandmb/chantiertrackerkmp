package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dmb.chantiertracker.presentation.navigation.CreateProjectRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectsRoute
import com.dmb.chantiertracker.presentation.navigation.SettingsRoute
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectScreen
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_project_title
import com.dmb.chantiertracker.resources.projects_new
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class MainDestination { Projects, Settings, CreateProject }

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
        else -> MainDestination.Projects
    }
    val currentTab = when (current) {
        MainDestination.Projects -> MainTab.Projects
        MainDestination.Settings -> MainTab.Settings
        MainDestination.CreateProject -> null
    }

    Scaffold(
        topBar = {
            when (current) {
                MainDestination.CreateProject -> DetailTopBar(
                    title = stringResource(Res.string.create_project_title),
                    onBack = { navController.popBackStack() },
                )
                else -> AppTopBar(
                    userName = account.userName,
                    email = account.email,
                    plan = account.plan,
                    onSubscription = {},
                    onLogout = viewModel::logout,
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
                ProjectsScreen(onProjectClick = {})
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<CreateProjectRoute> {
                CreateProjectScreen()
            }
        }
    }
}
