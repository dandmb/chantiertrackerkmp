package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AccountMenuBody
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.AppBottomBar
import com.dmb.chantiertracker.presentation.main.AppTopBar
import com.dmb.chantiertracker.presentation.main.DetailTopBar
import com.dmb.chantiertracker.presentation.main.MainTab
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectScreen
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.presentation.settings.SettingsViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class MainScreensSnapshotTest {

    private val outDir = File("build/auth-snapshots").apply { mkdirs() }

    private val sampleProjects = listOf(
        Project(1, "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS),
        Project(2, "Hangar logistique Est", null, "Béziers", ProjectStatus.SUSPENDED),
        Project(3, "Réfection toiture Marchand", null, null, ProjectStatus.COMPLETED),
    )

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun snapshot(name: String, locale: String, dark: Boolean = false, content: @Composable () -> Unit) =
        runComposeUiTest {
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme(darkTheme = dark) {
                        Box(Modifier.size(412.dp, 892.dp)) { content() }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(outDir, "$name-$locale.png"))
        }

    @Composable
    private fun Chrome(tab: MainTab, screen: @Composable (Modifier) -> Unit) {
        Scaffold(
            topBar = {
                AppTopBar(
                    userName = "Jean Marchand",
                    email = "jean@chantier.dev",
                    plan = Plan.LIBERTE,
                    onSubscription = {},
                    onLogout = {},
                )
            },
            bottomBar = { AppBottomBar(current = tab, onSelect = {}) },
            floatingActionButton = {
                if (tab == MainTab.Projects) {
                    FloatingActionButton(onClick = {}) { Icon(AddIcon, contentDescription = null) }
                }
            },
        ) { padding -> screen(Modifier.padding(padding)) }
    }

    @Composable
    private fun DetailChrome(title: String, screen: @Composable (Modifier) -> Unit) {
        Scaffold(
            topBar = { DetailTopBar(title = title, onBack = {}) },
        ) { padding -> screen(Modifier.padding(padding)) }
    }

    private fun projectsVm(projects: List<Project>) = ProjectsViewModel(FakeProjectRepository(projects = projects))
    private fun settingsVm() = SettingsViewModel(AppConfig(FakeBuildInfo(isDebug = false, appVersion = "1.0")))

    @Test
    fun capture_main_screens_in_french_and_english() {
        for (locale in listOf("fr", "en")) {
            snapshot("10-projects-list", locale) {
                Chrome(MainTab.Projects) { m ->
                    ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(sampleProjects))
                }
            }
            snapshot("11-projects-empty", locale) {
                Chrome(MainTab.Projects) { m ->
                    ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(emptyList()))
                }
            }
            snapshot("12-settings", locale) {
                Chrome(MainTab.Settings) { m -> SettingsScreen(modifier = m, viewModel = settingsVm()) }
            }
        }
        snapshot("13-projects-list-dark", "fr", dark = true) {
            Chrome(MainTab.Projects) { m ->
                ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(sampleProjects))
            }
        }
        for (locale in listOf("fr", "en")) {
            snapshot("14-create-project", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Nouveau projet" else "New project",
                ) { m -> CreateProjectScreen(modifier = m) }
            }
        }
        for (locale in listOf("fr", "en")) {
            snapshot("15-account-menu", locale) {
                Box(Modifier.padding(12.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Column(Modifier.width(260.dp)) {
                            AccountMenuBody(
                                userName = "Jean Marchand",
                                email = "jean@chantier.dev",
                                plan = Plan.LIBERTE,
                                onSubscription = {},
                                onLogout = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
