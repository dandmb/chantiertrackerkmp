package com.dmb.chantiertracker.presentation.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.presentation.main.ChevronRightIcon
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerDark
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerLight
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberDark
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberLight
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.project_open
import com.dmb.chantiertracker.resources.project_status_completed
import com.dmb.chantiertracker.resources.project_status_in_progress
import com.dmb.chantiertracker.resources.project_status_suspended
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProjectCard(project: Project, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!project.location.isNullOrBlank()) {
                    Text(
                        text = project.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(project.status)
            }
            Icon(
                imageVector = ChevronRightIcon,
                contentDescription = stringResource(Res.string.project_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(status: ProjectStatus) {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (container, content, label) = when (status) {
        ProjectStatus.IN_PROGRESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Res.string.project_status_in_progress,
        )
        ProjectStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Res.string.project_status_completed,
        )
        else -> Triple(
            if (darkSurface) WarningAmberContainerDark else WarningAmberContainerLight,
            if (darkSurface) WarningOnAmberDark else WarningOnAmberLight,
            Res.string.project_status_suspended,
        )
    }
    StatusChip(container, content, label)
}

@Composable
private fun StatusChip(container: Color, content: Color, label: StringResource) {
    Surface(color = container, shape = RoundedCornerShape(percent = 50)) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}
