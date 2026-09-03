package com.dmb.chantiertracker.presentation.stages

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerDark
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerLight
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberDark
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberLight
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.stage_status_completed
import com.dmb.chantiertracker.resources.stage_status_in_progress
import org.jetbrains.compose.resources.stringResource

@Composable
fun StageStatusBadge(status: StageStatus, modifier: Modifier = Modifier) {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (container, content, labelRes) = when (status) {
        StageStatus.IN_PROGRESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Res.string.stage_status_in_progress,
        )
        StageStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Res.string.stage_status_completed,
        )
        else -> Triple(
            if (darkSurface) WarningAmberContainerDark else WarningAmberContainerLight,
            if (darkSurface) WarningOnAmberDark else WarningOnAmberLight,
            Res.string.stage_status_in_progress,
        )
    }
    Surface(modifier = modifier, color = container, shape = RoundedCornerShape(percent = 50)) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}
