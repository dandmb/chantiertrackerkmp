package com.dmb.chantiertracker.presentation.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.presentation.main.ArrowDropDownIcon
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerDark
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerLight
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberDark
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberLight
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.project_status_completed
import com.dmb.chantiertracker.resources.project_status_in_progress
import com.dmb.chantiertracker.resources.project_status_suspended
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun ProjectStatus.labelRes(): StringResource = when (this) {
    ProjectStatus.IN_PROGRESS -> Res.string.project_status_in_progress
    ProjectStatus.COMPLETED -> Res.string.project_status_completed
    ProjectStatus.SUSPENDED, ProjectStatus.UNKNOWN -> Res.string.project_status_suspended
}

@Composable
private fun projectStatusColors(status: ProjectStatus): Pair<Color, Color> {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (status) {
        ProjectStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ProjectStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ProjectStatus.SUSPENDED, ProjectStatus.UNKNOWN -> (if (darkSurface) WarningAmberContainerDark else WarningAmberContainerLight) to
            (if (darkSurface) WarningOnAmberDark else WarningOnAmberLight)
    }
}

@Composable
fun ProjectStatusBadge(status: ProjectStatus, modifier: Modifier = Modifier) {
    val (container, content) = projectStatusColors(status)
    Surface(modifier = modifier, color = container, shape = RoundedCornerShape(percent = 50)) {
        Text(
            text = stringResource(status.labelRes()),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}

private val EDITABLE_PROJECT_STATUSES =
    listOf(ProjectStatus.IN_PROGRESS, ProjectStatus.SUSPENDED, ProjectStatus.COMPLETED)

// ADMIN-only control mirroring the web's inline Select on ProjectDetailPage
// (never on the edit form) — a non-admin (or a read-only context such as
// ProjectCard) always gets the plain ProjectStatusBadge, never this menu.
@Composable
fun ProjectStatusMenu(
    current: ProjectStatus,
    editable: Boolean,
    onSelect: (ProjectStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!editable) {
        ProjectStatusBadge(current, modifier)
        return
    }
    var open by remember { mutableStateOf(false) }
    val (container, content) = projectStatusColors(current)
    Box(modifier) {
        Surface(onClick = { open = true }, color = container, shape = RoundedCornerShape(percent = 50)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(current.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = content,
                )
                Icon(ArrowDropDownIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EDITABLE_PROJECT_STATUSES.forEach { status ->
                DropdownMenuItem(
                    text = { Text(stringResource(status.labelRes())) },
                    onClick = {
                        open = false
                        onSelect(status)
                    },
                    trailingIcon = {
                        if (status == current) {
                            Icon(
                                CheckIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}
