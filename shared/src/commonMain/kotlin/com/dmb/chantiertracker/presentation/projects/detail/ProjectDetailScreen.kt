package com.dmb.chantiertracker.presentation.projects.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.projects.ProjectLocation
import com.dmb.chantiertracker.presentation.projects.ProjectStatusBadge
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.detail_currency
import com.dmb.chantiertracker.resources.detail_edit
import com.dmb.chantiertracker.resources.detail_members_empty
import com.dmb.chantiertracker.resources.detail_section_members
import com.dmb.chantiertracker.resources.detail_section_stages
import com.dmb.chantiertracker.resources.detail_stages_empty
import com.dmb.chantiertracker.resources.detail_timezone
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.projects_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProjectDetailScreen(
    projectLocalId: String,
    modifier: Modifier = Modifier,
    onProjectNameResolved: (String) -> Unit = {},
    viewModel: ProjectDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }
    LaunchedEffect(state.detail?.name) {
        state.detail?.name?.let(onProjectNameResolved)
    }

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
            state.detail != null -> DetailContent(state.detail!!, state.canEdit)
        }
    }
}

@Composable
private fun DetailContent(detail: ProjectDetail, canEdit: Boolean) {
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
                    OutlinedButton(onClick = {}, enabled = false) {
                        Icon(EditIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(Res.string.detail_edit),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            ProjectStatusBadge(detail.status)
            if (!detail.location.isNullOrBlank()) {
                ProjectLocation(detail.location, style = MaterialTheme.typography.bodyMedium)
            }
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

        Section(
            title = stringResource(Res.string.detail_section_stages),
            emptyMessage = stringResource(Res.string.detail_stages_empty),
        )
        Section(
            title = stringResource(Res.string.detail_section_members),
            emptyMessage = stringResource(Res.string.detail_members_empty),
        )
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
private fun Section(title: String, emptyMessage: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = emptyMessage,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
