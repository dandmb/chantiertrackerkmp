package com.dmb.chantiertracker.presentation.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.actions
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.sync_acknowledge_body
import com.dmb.chantiertracker.resources.sync_acknowledge_title
import com.dmb.chantiertracker.resources.sync_action_working
import com.dmb.chantiertracker.resources.sync_discard_body
import com.dmb.chantiertracker.resources.sync_discard_file
import com.dmb.chantiertracker.resources.sync_discard_title
import com.dmb.chantiertracker.resources.sync_linked_removed
import com.dmb.chantiertracker.resources.sync_revert_offline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.canBeRetried
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.main.CalendarIcon
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.CloseIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.ProjectsIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_close
import com.dmb.chantiertracker.resources.sync_group_day
import com.dmb.chantiertracker.resources.sync_group_stage
import com.dmb.chantiertracker.resources.sync_issue_retry
import com.dmb.chantiertracker.resources.sync_issue_retrying
import com.dmb.chantiertracker.resources.sync_issues_count
import com.dmb.chantiertracker.resources.sync_issues_empty_body
import com.dmb.chantiertracker.resources.sync_issues_empty_title
import com.dmb.chantiertracker.resources.sync_issues_intro
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SyncIssuesScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncIssuesViewModel = koinViewModel(),
    onFix: (SyncIssueItem) -> Unit = {},
    focusKey: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = IssueActions(
        onFix = onFix,
        onRetry = viewModel::retry,
        onRevert = viewModel::revert,
        onDiscard = viewModel::discard,
        onAcknowledge = viewModel::acknowledge,
    )

    val listState = rememberLazyListState()
    val focusIndex = focusKey?.let { key -> state.rowKeys().indexOf("issue:$key") } ?: -1
    var focusShown by rememberSaveable(focusKey) { mutableStateOf(false) }
    LaunchedEffect(focusKey, focusIndex >= 0) {
        if (!focusShown && focusIndex >= 0) {
            listState.scrollToItem(focusIndex)
            focusShown = true
        }
    }

    state.confirmation?.let { confirmation ->
        ConfirmRemovalDialog(confirmation, onConfirm = viewModel::confirm, onDismiss = viewModel::dismissConfirmation)
    }

    ResponsiveContent(modifier) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.isEmpty -> NothingToReview(state, onDismissNotice = viewModel::dismissNotice)
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.notice != null || state.actionNotice != null) {
                    item(key = "notice") { Notice(state, onDismiss = viewModel::dismissNotice) }
                }
                item(key = "summary") { Summary(state.total) }
                state.projects.forEach { project -> projectGroup(project, state, actions, focusKey) }
            }
        }
    }
}

private class IssueActions(
    val onFix: (SyncIssueItem) -> Unit,
    val onRetry: (SyncIssueItem) -> Unit,
    val onRevert: (SyncIssueItem) -> Unit,
    val onDiscard: (SyncIssueItem) -> Unit,
    val onAcknowledge: (SyncIssueItem) -> Unit,
) {
    fun run(action: SyncIssueAction, item: SyncIssueItem) = when (action) {
        SyncIssueAction.FIX -> onFix(item)
        SyncIssueAction.RETRY -> onRetry(item)
        SyncIssueAction.REVERT -> onRevert(item)
        SyncIssueAction.DISCARD -> onDiscard(item)
        SyncIssueAction.ACKNOWLEDGE -> onAcknowledge(item)
    }
}

private fun SyncIssuesUiState.rowKeys(): List<String> = buildList {
    if (notice != null || actionNotice != null) add("notice")
    add("summary")
    projects.forEach { project ->
        add("project:${project.projectLocalId}")
        project.items.forEach { add("issue:${it.key}") }
        project.stages.forEach { stage ->
            add("stage:${stage.stageLocalId}")
            stage.items.forEach { add("issue:${it.key}") }
            stage.days.forEach { day ->
                add("day:${day.dailyLogLocalId}")
                day.items.forEach { add("issue:${it.key}") }
            }
        }
    }
}

private fun LazyListScope.projectGroup(project: SyncIssueProjectGroup, state: SyncIssuesUiState, actions: IssueActions, focusKey: String?) {
    item(key = "project:${project.projectLocalId}") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GroupTitle(project.projectName, ProjectsIcon, emphasis = GroupEmphasis.PROJECT)
        }
    }
    issueCards(project.items, state, actions, focusKey)
    project.stages.forEach { stage ->
        item(key = "stage:${stage.stageLocalId}") {
            GroupTitle(stringResource(Res.string.sync_group_stage, stage.stageName), ConstructionIcon, emphasis = GroupEmphasis.STAGE)
        }
        issueCards(stage.items, state, actions, focusKey)
        stage.days.forEach { day ->
            item(key = "day:${day.dailyLogLocalId}") {
                GroupTitle(stringResource(Res.string.sync_group_day, formatIsoDate(day.date)), CalendarIcon, emphasis = GroupEmphasis.DAY)
            }
            issueCards(day.items, state, actions, focusKey)
        }
    }
}

private fun LazyListScope.issueCards(items: List<SyncIssueItem>, state: SyncIssuesUiState, actions: IssueActions, focusKey: String?) {
    items.forEach { item ->
        item(key = "issue:${item.key}") { IssueCard(item, state, actions, singledOut = item.key == focusKey) }
    }
}

@Composable
private fun Summary(total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = pluralStringResource(Res.plurals.sync_issues_count, total, total),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.sync_issues_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class GroupEmphasis { PROJECT, STAGE, DAY }

@Composable
private fun GroupTitle(text: String, icon: ImageVector, emphasis: GroupEmphasis) {
    val style = when (emphasis) {
        GroupEmphasis.PROJECT -> MaterialTheme.typography.titleMedium
        GroupEmphasis.STAGE -> MaterialTheme.typography.titleSmall
        GroupEmphasis.DAY -> MaterialTheme.typography.labelLarge
    }
    val color = if (emphasis == GroupEmphasis.DAY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (emphasis == GroupEmphasis.PROJECT) 0.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (emphasis == GroupEmphasis.PROJECT) 20.dp else 18.dp))
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueCard(item: SyncIssueItem, state: SyncIssuesUiState, actions: IssueActions, singledOut: Boolean) {
    val offered = item.actions
    val revertUnavailable = SyncIssueAction.REVERT in offered && !state.canRevert(item)
    Card(
        modifier = if (singledOut) Modifier.fillMaxWidth().semantics { selected = true } else Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (singledOut) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLabel(item.issue.kind)
            Text(
                text = item.title(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.sentence(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.typedVersusServer()?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            item.issue.instruction()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (revertUnavailable) {
                Text(
                    text = stringResource(Res.string.sync_revert_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (offered.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    offered.forEach { action ->
                        ActionButton(
                            action = action,
                            enabled = !state.isBusy && !(action == SyncIssueAction.REVERT && revertUnavailable),
                            isRetrying = action == SyncIssueAction.RETRY && state.retryingKey == item.key,
                            isWorking = action == offered.last() && action != SyncIssueAction.RETRY && state.busyKey == item.key,
                            onClick = { actions.run(action, item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: SyncIssueAction, enabled: Boolean, isRetrying: Boolean, isWorking: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when {
            isRetrying -> Res.string.sync_issue_retrying
            isWorking -> Res.string.sync_action_working
            else -> action.labelRes()
        },
    )
    val content: @Composable () -> Unit = {
        if (isRetrying || isWorking) {
            CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
        }
        Text(label)
    }
    val size = Modifier.heightIn(min = 48.dp)
    when (action) {
        SyncIssueAction.FIX -> FilledTonalButton(onClick = onClick, enabled = enabled, modifier = size) { content() }
        SyncIssueAction.DISCARD -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = size,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { content() }
        SyncIssueAction.RETRY, SyncIssueAction.REVERT, SyncIssueAction.ACKNOWLEDGE ->
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = size) { content() }
    }
}

@Composable
private fun ConfirmRemovalDialog(confirmation: SyncIssueConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val discarding = confirmation.action == SyncIssueAction.DISCARD
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (discarding) Res.string.sync_discard_title else Res.string.sync_acknowledge_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (discarding) Res.string.sync_discard_body else Res.string.sync_acknowledge_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (confirmation.linkedCount > 0) {
                    Text(
                        pluralStringResource(Res.plurals.sync_linked_removed, confirmation.linkedCount, confirmation.linkedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (confirmation.item.target == SyncIssueTarget.ATTACHMENT) {
                    Text(stringResource(Res.string.sync_discard_file), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(confirmation.action.labelRes())) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun StatusLabel(kind: SyncIssueKind) {
    val waiting = kind == SyncIssueKind.BLOCKED_BY_PARENT
    val container = if (waiting) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (waiting) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text = stringResource(kind.statusRes()),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Notice(state: SyncIssuesUiState, onDismiss: () -> Unit) {
    val text = state.actionNotice?.let { stringResource(it.noticeRes()) } ?: state.notice?.let { stringResource(it.noticeRes()) } ?: return
    val positive = state.actionNotice?.let {
        it == SyncIssueActionNotice.DISCARDED || it == SyncIssueActionNotice.ACKNOWLEDGED || it == SyncIssueActionNotice.REVERTED
    } ?: (state.notice == RetryOutcome.ACCEPTED)
    val container = if (positive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (positive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            IconButton(onClick = onDismiss) {
                Icon(CloseIcon, contentDescription = stringResource(Res.string.action_close), tint = content)
            }
        }
    }
}

@Composable
private fun NothingToReview(state: SyncIssuesUiState, onDismissNotice: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Notice(state, onDismiss = onDismissNotice)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(CheckIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(Res.string.sync_issues_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.sync_issues_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
