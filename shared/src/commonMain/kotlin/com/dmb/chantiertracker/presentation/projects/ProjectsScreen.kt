package com.dmb.chantiertracker.presentation.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.incoming_invitation_accept
import com.dmb.chantiertracker.resources.incoming_invitation_body
import com.dmb.chantiertracker.resources.incoming_invitation_body_with_inviter
import com.dmb.chantiertracker.resources.projects_empty_body
import com.dmb.chantiertracker.resources.projects_empty_title
import com.dmb.chantiertracker.resources.role_admin
import com.dmb.chantiertracker.resources.role_supervisor
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onProjectClick: (projectLocalId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEnter() }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.incomingInvitations.isNotEmpty() || state.invitationError != null) {
                IncomingInvitations(
                    invitations = state.incomingInvitations,
                    acceptingTokens = state.acceptingTokens,
                    error = state.invitationError?.localizedText(),
                    onAccept = viewModel::acceptInvitation,
                )
            }
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
                state.isEmpty -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.projects, key = { it.localId }) { project ->
                        ProjectCard(project = project, onClick = { onProjectClick(project.localId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingInvitations(
    invitations: List<IncomingInvitation>,
    acceptingTokens: Set<String>,
    error: String?,
    onAccept: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        error?.let { ErrorBanner(it) }
        invitations.forEach { invitation ->
            IncomingInvitationCard(
                invitation = invitation,
                accepting = invitation.token in acceptingTokens,
                onAccept = { onAccept(invitation.token) },
            )
        }
    }
}

@Composable
private fun IncomingInvitationCard(
    invitation: IncomingInvitation,
    accepting: Boolean,
    onAccept: () -> Unit,
) {
    val roleLabel = when (invitation.role) {
        ProjectRole.ADMIN -> stringResource(Res.string.role_admin)
        ProjectRole.SUPERVISOR -> stringResource(Res.string.role_supervisor)
        ProjectRole.UNKNOWN -> ""
    }
    val message = invitation.invitedByName?.let {
        stringResource(Res.string.incoming_invitation_body_with_inviter, invitation.projectName, roleLabel, it)
    } ?: stringResource(Res.string.incoming_invitation_body, invitation.projectName, roleLabel)

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = onAccept,
                enabled = !accepting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.incoming_invitation_accept))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    // Scrollable so the pull-to-refresh gesture is available on the empty state too.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.projects_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.projects_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
