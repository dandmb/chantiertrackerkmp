package com.dmb.chantiertracker.presentation.sync

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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ResponsiveContent(modifier) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.isEmpty -> NothingToReview(notice = state.notice, onDismissNotice = viewModel::dismissNotice)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.notice?.let { notice ->
                    item(key = "notice") { RetryNotice(notice, onDismiss = viewModel::dismissNotice) }
                }
                item(key = "summary") { Summary(state.total) }
                state.projects.forEach { project ->
                    projectGroup(project, retryingKey = state.retryingKey, onRetry = viewModel::retry)
                }
            }
        }
    }
}

private fun LazyListScope.projectGroup(project: SyncIssueProjectGroup, retryingKey: String?, onRetry: (SyncIssueItem) -> Unit) {
    item(key = "project:${project.projectLocalId}") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GroupTitle(project.projectName, ProjectsIcon, emphasis = GroupEmphasis.PROJECT)
        }
    }
    issueCards(project.items, retryingKey, onRetry)
    project.stages.forEach { stage ->
        item(key = "stage:${stage.stageLocalId}") {
            GroupTitle(stringResource(Res.string.sync_group_stage, stage.stageName), ConstructionIcon, emphasis = GroupEmphasis.STAGE)
        }
        issueCards(stage.items, retryingKey, onRetry)
        stage.days.forEach { day ->
            item(key = "day:${day.dailyLogLocalId}") {
                GroupTitle(stringResource(Res.string.sync_group_day, formatIsoDate(day.date)), CalendarIcon, emphasis = GroupEmphasis.DAY)
            }
            issueCards(day.items, retryingKey, onRetry)
        }
    }
}

private fun LazyListScope.issueCards(items: List<SyncIssueItem>, retryingKey: String?, onRetry: (SyncIssueItem) -> Unit) {
    items.forEach { item ->
        item(key = "issue:${item.key}") {
            IssueCard(item, isRetrying = retryingKey == item.key, retryEnabled = retryingKey == null, onRetry = { onRetry(item) })
        }
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

@Composable
private fun IssueCard(item: SyncIssueItem, isRetrying: Boolean, retryEnabled: Boolean, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
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
                text = item.issue.sentence(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.typedVersusServer()?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            item.issue.instruction()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.issue.canBeRetried) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(if (isRetrying) Res.string.sync_issue_retrying else Res.string.sync_issue_retry))
                }
            }
        }
    }
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
private fun RetryNotice(notice: RetryOutcome, onDismiss: () -> Unit) {
    val accepted = notice == RetryOutcome.ACCEPTED
    val container = if (accepted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (accepted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(notice.noticeRes()),
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
private fun NothingToReview(notice: RetryOutcome?, onDismissNotice: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        notice?.let { RetryNotice(it, onDismiss = onDismissNotice) }
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
