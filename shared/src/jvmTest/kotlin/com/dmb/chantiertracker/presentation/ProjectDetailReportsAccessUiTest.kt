package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProjectDetailReportsAccessUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun vm(currentUserIsOwner: Boolean): ProjectDetailViewModel {
        val auth = FakeAuthRepository().apply {
            emitState(AuthState.Authenticated(User(1, "jean@x.dev", "Jean", true, GlobalRole.USER)))
        }
        val repo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "p1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = if (currentUserIsOwner) 1L else 999L,
            ),
            members = listOf(
                ProjectMember(userId = 1, name = "Jean", email = "jean@x.dev", role = if (currentUserIsOwner) ProjectRole.ADMIN else ProjectRole.SUPERVISOR),
            ),
        )
        return ProjectDetailViewModel(repo, FakeStageRepository(), FakeInvitationRepository(), auth).also { it.load("p1") }
    }

    @Test
    fun an_admin_can_open_the_reports_list() = runComposeUiTest {
        var opened: String? = null
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        ProjectDetailScreen("p1", viewModel = vm(currentUserIsOwner = true), onOpenReports = { opened = it })
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText("Signalements")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Problèmes signalés sur les entrées").performScrollTo().performClick()

        assertEquals("p1", opened)
    }

    @Test
    fun a_supervisor_does_not_see_the_reports_section() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        ProjectDetailScreen("p1", viewModel = vm(currentUserIsOwner = false))
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText("Villa Vidal", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Signalements").assertDoesNotExist()
    }
}
