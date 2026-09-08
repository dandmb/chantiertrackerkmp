package com.dmb.chantiertracker.presentation.projects.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.DetailEmptyHint
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.DownloadIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.detail_export_action
import com.dmb.chantiertracker.resources.detail_export_generating
import com.dmb.chantiertracker.resources.detail_export_hint
import com.dmb.chantiertracker.resources.detail_export_title
import com.dmb.chantiertracker.resources.detail_export_upgrade
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// A "Dossier de chantier" DetailSection on ProjectDetailScreen — visible to any
// project member (not ADMIN-only: the backend and the web gate on the OWNER's
// plan, not the caller's role). SEMI_FLEX/LIBERTE → an "Export to PDF" button;
// FREE/UNKNOWN → a calm upgrade hint (no billing screen, like the history
// retention notice). The parent only renders this when `ownerPlan` is known.
@Composable
fun ExportSection(
    projectLocalId: String,
    ownerPlan: Plan,
    viewModel: ProjectExportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canExport = ownerPlan == Plan.SEMI_FLEX || ownerPlan == Plan.LIBERTE

    DetailSection(stringResource(Res.string.detail_export_title)) {
        if (!canExport) {
            DetailEmptyHint(stringResource(Res.string.detail_export_upgrade))
            return@DetailSection
        }

        Text(
            text = stringResource(Res.string.detail_export_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.error?.let { ErrorBanner(it.localizedText()) }

        if (state.isExporting) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.detail_export_generating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OutlinedButton(onClick = { viewModel.export(projectLocalId) }) {
                Icon(DownloadIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(Res.string.detail_export_action), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
