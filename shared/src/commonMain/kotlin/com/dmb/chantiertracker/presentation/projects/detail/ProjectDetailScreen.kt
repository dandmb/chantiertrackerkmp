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
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.presentation.ClickableListRow
import com.dmb.chantiertracker.presentation.ConfirmActionDialog
import com.dmb.chantiertracker.presentation.DetailEmptyHint
import com.dmb.chantiertracker.presentation.DetailInfoRow
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.WidthSizeClass
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.presentation.projects.ProjectLocation
import com.dmb.chantiertracker.presentation.projects.ProjectStatusMenu
import com.dmb.chantiertracker.presentation.projects.export.ExportSection
import com.dmb.chantiertracker.presentation.stages.StageStatusBadge
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.detail_currency
import com.dmb.chantiertracker.resources.detail_danger_zone
import com.dmb.chantiertracker.resources.detail_danger_zone_body
import com.dmb.chantiertracker.resources.detail_delete_project
import com.dmb.chantiertracker.resources.detail_edit_project
import com.dmb.chantiertracker.resources.detail_history
import com.dmb.chantiertracker.resources.detail_history_hint
import com.dmb.chantiertracker.resources.detail_reports
import com.dmb.chantiertracker.resources.detail_reports_hint
import com.dmb.chantiertracker.resources.detail_invitation_cancel
import com.dmb.chantiertracker.resources.detail_invitation_cancel_confirm_body
import com.dmb.chantiertracker.resources.detail_invitation_cancel_confirm_button
import com.dmb.chantiertracker.resources.detail_invitation_cancel_confirm_title
import com.dmb.chantiertracker.resources.detail_invitation_sent_on
import com.dmb.chantiertracker.resources.detail_invitations_empty
import com.dmb.chantiertracker.resources.detail_invitations_title
import com.dmb.chantiertracker.resources.detail_invite_member
import com.dmb.chantiertracker.resources.detail_members_empty
import com.dmb.chantiertracker.resources.detail_section_members
import com.dmb.chantiertracker.resources.role_admin
import com.dmb.chantiertracker.resources.role_supervisor
import com.dmb.chantiertracker.resources.detail_section_info
import com.dmb.chantiertracker.resources.detail_section_stages
import com.dmb.chantiertracker.resources.detail_stages_add
import com.dmb.chantiertracker.resources.detail_stages_empty
import com.dmb.chantiertracker.resources.detail_timezone
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.project_location_unset
import com.dmb.chantiertracker.resources.projects_retry
import com.dmb.chantiertracker.resources.stage_card_budget
import org.jetbrains.compose.resources.StringResource
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
    onOpenHistory: (projectLocalId: String) -> Unit = {},
    onOpenReports: (projectLocalId: String) -> Unit = {},
    onProjectDeleted: () -> Unit = {},
    viewModel: ProjectDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }
    LaunchedEffect(state.detail?.name) {
        state.detail?.name?.let(onProjectNameResolved)
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onProjectDeleted() }

    ResponsiveContent(modifier, maxContentWidth = 900.dp) { widthClass ->
        Box(Modifier.fillMaxSize()) {
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
                    twoColumns = widthClass == WidthSizeClass.EXPANDED,
                    onAddStage = { onAddStage(state.detail!!.localId) },
                    onStageClick = onStageClick,
                    onEditProject = { onEditProject(state.detail!!.localId) },
                    onInviteMember = { onInviteMember(state.detail!!.localId) },
                    onOpenHistory = { onOpenHistory(state.detail!!.localId) },
                    onOpenReports = { onOpenReports(state.detail!!.localId) },
                    onCancelInvitation = viewModel::cancelInvitation,
                    onDeleteConfirmed = viewModel::deleteProject,
                    onStatusChange = viewModel::changeStatus,
                )
            }
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
    // Two independent, genuinely parallel groups of sections (project info +
    // stages vs. members + invitations/history/reports) — a real 2-column
    // layout on a wide window rather than just a width cap, unlike the
    // single-column forms/lists elsewhere in this same sous-étape (ADR-56
    // sous-étape 3/5). EXPANDED only (840dp+): MEDIUM stays single-column,
    // narrow enough that two columns would cramp both.
    twoColumns: Boolean,
    onAddStage: () -> Unit,
    onStageClick: (String) -> Unit,
    onEditProject: () -> Unit,
    onInviteMember: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onCancelInvitation: (Long) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onStatusChange: (ProjectStatus) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ProjectHeader(detail = detail, canEdit = canEdit, onEditProject = onEditProject, onStatusChange = onStatusChange)

        DetailSectionDivider()

        // Mirrors the web (ProjectDetailPage.tsx): export lives above the
        // section grid entirely, a standalone project-level action, never
        // grouped under "Informations" — same placement regardless of
        // column count. Owner's plan unknown until the first detail pull
        // (ADR-33) — same as the web, which renders nothing until
        // `project.ownerPlan` is set.
        detail.ownerPlan?.let { ownerPlan ->
            ExportSection(projectLocalId = detail.localId, ownerPlan = ownerPlan)
            DetailSectionDivider()
        }

        if (twoColumns) {
            // Paired by relatedness (facts / people / admin nav shortcuts)
            // rather than bucketed into two independent stacks — a 2-vs-4
            // split there left one column much taller than the other, an
            // uneven, disordered-looking result at 1440px (found by actually
            // capturing this screen at that width, not guessed).
            TwoColumnRow(
                left = { InfoSection(detail) },
                right = { StagesSection(stages, detail.currency, onAddStage, onStageClick) },
            )
            if (isAdmin) {
                DetailSectionDivider()
                TwoColumnRow(
                    left = { MembersSection(members, isAdmin, onInviteMember) },
                    right = {
                        InvitationsSection(pendingInvitations, cancellingInvitationIds, invitationActionError, onCancelInvitation)
                    },
                )
                DetailSectionDivider()
                TwoColumnRow(
                    left = { HistorySection(onOpenHistory) },
                    right = { ReportsSection(onOpenReports) },
                )
            } else {
                DetailSectionDivider()
                MembersSection(members, isAdmin, onInviteMember)
            }
        } else {
            InfoSection(detail)

            DetailSectionDivider()
            StagesSection(stages, detail.currency, onAddStage, onStageClick)

            DetailSectionDivider()
            MembersSection(members, isAdmin, onInviteMember)

            if (isAdmin) {
                DetailSectionDivider()
                InvitationsSection(pendingInvitations, cancellingInvitationIds, invitationActionError, onCancelInvitation)

                DetailSectionDivider()
                HistorySection(onOpenHistory)

                DetailSectionDivider()
                ReportsSection(onOpenReports)
            }
        }

        if (canEdit) {
            DetailSectionDivider()
            DangerZone(projectName = detail.name, isDeleting = isDeleting, onDeleteConfirmed = onDeleteConfirmed)
        }
    }
}

// EXPANDED only — pairs two related sections side by side instead of
// stacking every left-column section above every right-column one, which
// left one column visibly taller/emptier than the other (found by
// capturing the previous layout at 1440px, not guessed).
@Composable
private fun TwoColumnRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

@Composable
private fun InfoSection(detail: ProjectDetail) {
    DetailSection(stringResource(Res.string.detail_section_info)) {
        DetailInfoRow(stringResource(Res.string.detail_currency), detail.currency)
        DetailInfoRow(stringResource(Res.string.detail_timezone), detail.timezone)
    }
}

@Composable
private fun StagesSection(stages: List<Stage>, currency: String, onAddStage: () -> Unit, onStageClick: (String) -> Unit) {
    DetailSection(
        title = stringResource(Res.string.detail_section_stages),
        action = { SectionTextAction(Res.string.detail_stages_add, onAddStage) },
    ) {
        if (stages.isEmpty()) {
            DetailEmptyHint(stringResource(Res.string.detail_stages_empty))
        } else {
            stages.forEach { stage ->
                StageRow(stage, currency = currency, onClick = { onStageClick(stage.localId) })
            }
        }
    }
}

@Composable
private fun MembersSection(members: List<ProjectMember>, isAdmin: Boolean, onInviteMember: () -> Unit) {
    DetailSection(
        title = stringResource(Res.string.detail_section_members),
        action = if (isAdmin) {
            { SectionTextAction(Res.string.detail_invite_member, onInviteMember) }
        } else {
            null
        },
    ) {
        if (members.isEmpty()) {
            DetailEmptyHint(stringResource(Res.string.detail_members_empty))
        } else {
            members.forEach { member -> MemberRow(member) }
        }
    }
}

@Composable
private fun InvitationsSection(
    pendingInvitations: List<Invitation>,
    cancellingInvitationIds: Set<Long>,
    invitationActionError: String?,
    onCancelInvitation: (Long) -> Unit,
) {
    var pendingCancelInvitation by remember { mutableStateOf<Invitation?>(null) }

    DetailSection(stringResource(Res.string.detail_invitations_title)) {
        invitationActionError?.let { ErrorBanner(it) }
        if (pendingInvitations.isEmpty()) {
            DetailEmptyHint(stringResource(Res.string.detail_invitations_empty))
        } else {
            pendingInvitations.forEach { invitation ->
                InvitationRow(
                    invitation = invitation,
                    isCancelling = invitation.id in cancellingInvitationIds,
                    onCancel = { pendingCancelInvitation = invitation },
                )
            }
        }
    }

    pendingCancelInvitation?.let { invitation ->
        ConfirmActionDialog(
            title = stringResource(Res.string.detail_invitation_cancel_confirm_title),
            body = stringResource(Res.string.detail_invitation_cancel_confirm_body, invitation.email),
            confirmLabel = stringResource(Res.string.detail_invitation_cancel_confirm_button),
            destructive = false,
            onDismiss = { pendingCancelInvitation = null },
            onConfirm = {
                pendingCancelInvitation = null
                onCancelInvitation(invitation.id)
            },
        )
    }
}

@Composable
private fun HistorySection(onOpenHistory: () -> Unit) {
    DetailSection(stringResource(Res.string.detail_history)) {
        ClickableListRow(onClick = onOpenHistory) {
            Text(
                text = stringResource(Res.string.detail_history_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReportsSection(onOpenReports: () -> Unit) {
    DetailSection(stringResource(Res.string.detail_reports)) {
        ClickableListRow(onClick = onOpenReports) {
            Text(
                text = stringResource(Res.string.detail_reports_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProjectHeader(
    detail: ProjectDetail,
    canEdit: Boolean,
    onEditProject: () -> Unit,
    onStatusChange: (ProjectStatus) -> Unit,
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
        ProjectStatusMenu(current = detail.status, editable = canEdit, onSelect = onStatusChange)
        ProjectLocation(
            location = detail.location?.takeIf { it.isNotBlank() }
                ?: stringResource(Res.string.project_location_unset),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!detail.description.isNullOrBlank()) {
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTextAction(label: StringResource, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = stringResource(label), modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun DangerZone(projectName: String, isDeleting: Boolean, onDeleteConfirmed: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
private fun StageRow(stage: Stage, currency: String, onClick: () -> Unit) {
    ClickableListRow(onClick = onClick) {
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
    }
}

@Composable
private fun MemberRow(member: ProjectMember) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RoleBadge(member.role)
    }
}

@Composable
private fun InvitationRow(invitation: Invitation, isCancelling: Boolean, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(invitation.email, style = MaterialTheme.typography.bodyMedium)
            invitation.createdAt?.let {
                Text(
                    text = stringResource(Res.string.detail_invitation_sent_on, formatIsoDate(it.substringBefore('T'))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onCancel, enabled = !isCancelling) {
            Text(stringResource(Res.string.detail_invitation_cancel))
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
