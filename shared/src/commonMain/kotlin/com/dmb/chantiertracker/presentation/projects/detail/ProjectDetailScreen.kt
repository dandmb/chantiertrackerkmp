package com.dmb.chantiertracker.presentation.projects.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.ChevronRightIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.presentation.projects.ProjectLocation
import com.dmb.chantiertracker.presentation.projects.ProjectStatusBadge
import com.dmb.chantiertracker.presentation.stages.StageStatusBadge
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.detail_currency
import com.dmb.chantiertracker.resources.detail_danger_zone
import com.dmb.chantiertracker.resources.detail_danger_zone_body
import com.dmb.chantiertracker.resources.detail_delete_project
import com.dmb.chantiertracker.resources.detail_edit_project
import com.dmb.chantiertracker.resources.detail_invitation_cancel
import com.dmb.chantiertracker.resources.detail_invitation_sent_on
import com.dmb.chantiertracker.resources.detail_invitations_empty
import com.dmb.chantiertracker.resources.detail_invitations_title
import com.dmb.chantiertracker.resources.detail_invite_member
import com.dmb.chantiertracker.resources.detail_members_empty
import com.dmb.chantiertracker.resources.detail_section_members
import com.dmb.chantiertracker.resources.role_admin
import com.dmb.chantiertracker.resources.role_supervisor
import com.dmb.chantiertracker.resources.detail_section_stages
import com.dmb.chantiertracker.resources.detail_stages_add
import com.dmb.chantiertracker.resources.detail_stages_empty
import com.dmb.chantiertracker.resources.detail_timezone
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.project_location_unset
import com.dmb.chantiertracker.resources.projects_retry
import com.dmb.chantiertracker.resources.stage_card_budget
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProjectDetailScreen(
    projectLocalId: String,
    modifier: Modifier = Modifier,
    onProjectNameResolved: (String) -> Unit = {},
    onAddStage: (projectLocalId: String) -> Unit = {},
    onStageClick: (stageLocalId: String) -> Unit = {},
    onEditProject: (projectLocalId: String) -> Unit = {},
    onInviteMember: (projectLocalId: String) -> Unit = {},
    onProjectDeleted: () -> Unit = {},
    viewModel: ProjectDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }
    LaunchedEffect(state.detail?.name) {
        state.detail?.name?.let(onProjectNameResolved)
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onProjectDeleted() }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.isMissing -> Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.error_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = viewModel::retry) {
                    Text(stringResource(Res.string.projects_retry))
                }
            }
            state.detail != null -> DetailContent(
                detail = state.detail!!,
                canEdit = state.canEdit,
                isAdmin = state.isAdmin,
                isDeleting = state.isDeleting,
                stages = state.stages,
                members = state.members,
                pendingInvitations = state.pendingInvitations,
                cancellingInvitationIds = state.cancellingInvitationIds,
                invitationActionError = state.invitationActionError?.localizedText(),
                onAddStage = { onAddStage(state.detail!!.localId) },
                onStageClick = onStageClick,
                onEditProject = { onEditProject(state.detail!!.localId) },
                onInviteMember = { onInviteMember(state.detail!!.localId) },
                onCancelInvitation = viewModel::cancelInvitation,
                onDeleteConfirmed = viewModel::deleteProject,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: ProjectDetail,
    canEdit: Boolean,
    isAdmin: Boolean,
    isDeleting: Boolean,
    stages: List<Stage>,
    members: List<ProjectMember>,
    pendingInvitations: List<Invitation>,
    cancellingInvitationIds: Set<Long>,
    invitationActionError: String?,
    onAddStage: () -> Unit,
    onStageClick: (String) -> Unit,
    onEditProject: () -> Unit,
    onInviteMember: () -> Unit,
    onCancelInvitation: (Long) -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    OutlinedButton(onClick = onEditProject) {
                        Icon(EditIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(Res.string.detail_edit_project),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            ProjectStatusBadge(detail.status)
            ProjectLocation(
                location = detail.location?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.project_location_unset),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!detail.description.isNullOrBlank()) {
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        InfoRow(stringResource(Res.string.detail_currency), detail.currency)
        InfoRow(stringResource(Res.string.detail_timezone), detail.timezone)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        StagesSection(
            stages = stages,
            currency = detail.currency,
            onAddStage = onAddStage,
            onStageClick = onStageClick,
        )

        MembersSection(
            members = members,
            pendingInvitations = pendingInvitations,
            isAdmin = isAdmin,
            cancellingInvitationIds = cancellingInvitationIds,
            invitationActionError = invitationActionError,
            onInviteMember = onInviteMember,
            onCancelInvitation = onCancelInvitation,
        )

        if (canEdit) {
            DangerZone(projectName = detail.name, isDeleting = isDeleting, onDeleteConfirmed = onDeleteConfirmed)
        }
    }
}

@Composable
private fun DangerZone(projectName: String, isDeleting: Boolean, onDeleteConfirmed: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.detail_danger_zone),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(Res.string.detail_danger_zone_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { showDialog = true },
            enabled = !isDeleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(Res.string.detail_delete_project))
        }
    }

    if (showDialog) {
        DeleteProjectDialog(
            projectName = projectName,
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onDeleteConfirmed()
            },
        )
    }
}

@Composable
private fun StagesSection(
    stages: List<Stage>,
    currency: String,
    onAddStage: () -> Unit,
    onStageClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.detail_section_stages),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onAddStage) {
                Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(Res.string.detail_stages_add),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (stages.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.detail_stages_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            stages.forEach { stage ->
                StageRow(stage, currency = currency, onClick = { onStageClick(stage.localId) })
            }
        }
    }
}

@Composable
private fun StageRow(stage: Stage, currency: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stage.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                stage.estimatedBudget?.let {
                    Text(
                        text = stringResource(Res.string.stage_card_budget, formatMoney(it, currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StageStatusBadge(stage.status)
            Icon(
                imageVector = ChevronRightIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MembersSection(
    members: List<ProjectMember>,
    pendingInvitations: List<Invitation>,
    isAdmin: Boolean,
    cancellingInvitationIds: Set<Long>,
    invitationActionError: String?,
    onInviteMember: () -> Unit,
    onCancelInvitation: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.detail_section_members),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isAdmin) {
                TextButton(onClick = onInviteMember) {
                    Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(Res.string.detail_invite_member),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        if (members.isEmpty()) {
            EmptyHint(stringResource(Res.string.detail_members_empty))
        } else {
            members.forEach { member ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(member.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RoleBadge(member.role)
                }
            }
        }

        if (isAdmin) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(Res.string.detail_invitations_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            invitationActionError?.let { ErrorBanner(it) }
            if (pendingInvitations.isEmpty()) {
                EmptyHint(stringResource(Res.string.detail_invitations_empty))
            } else {
                pendingInvitations.forEach { invitation ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(invitation.email, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                invitation.createdAt?.let { stringResource(Res.string.detail_invitation_sent_on, formatIsoDate(it.substringBefore('T'))) }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { onCancelInvitation(invitation.id) },
                            enabled = invitation.id !in cancellingInvitationIds,
                        ) {
                            Text(stringResource(Res.string.detail_invitation_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: ProjectRole) {
    val label = when (role) {
        ProjectRole.ADMIN -> stringResource(Res.string.role_admin)
        ProjectRole.SUPERVISOR -> stringResource(Res.string.role_supervisor)
        ProjectRole.UNKNOWN -> "—"
    }
    Surface(
        color = if (role == ProjectRole.ADMIN) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (role == ProjectRole.ADMIN) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
