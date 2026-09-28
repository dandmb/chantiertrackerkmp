package com.dmb.chantiertracker.presentation.stages

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.presentation.main.ArrowDropDownIcon
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.stage_status_completed
import com.dmb.chantiertracker.resources.stage_status_in_progress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// StageStatus has only two meaningful values (no SUSPENDED, unlike
// ProjectStatus) — UNKNOWN falls back to IN_PROGRESS on both label and
// color, never a mismatched pairing of the two.
private fun StageStatus.labelRes(): StringResource = when (this) {
    StageStatus.COMPLETED -> Res.string.stage_status_completed
    StageStatus.IN_PROGRESS, StageStatus.UNKNOWN -> Res.string.stage_status_in_progress
}

@Composable
private fun stageStatusColors(status: StageStatus): Pair<Color, Color> = when (status) {
    StageStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    StageStatus.IN_PROGRESS, StageStatus.UNKNOWN ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
fun StageStatusBadge(status: StageStatus, modifier: Modifier = Modifier) {
    val (container, content) = stageStatusColors(status)
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

private val EDITABLE_STAGE_STATUSES = listOf(StageStatus.IN_PROGRESS, StageStatus.COMPLETED)

// ADMIN-only control mirroring the web's inline Select on StageDetailPage —
// a non-admin always gets the plain StageStatusBadge, never this menu.
@Composable
fun StageStatusMenu(
    current: StageStatus,
    editable: Boolean,
    onSelect: (StageStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!editable) {
        StageStatusBadge(current, modifier)
        return
    }
    var open by remember { mutableStateOf(false) }
    val (container, content) = stageStatusColors(current)
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
            EDITABLE_STAGE_STATUSES.forEach { status ->
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
