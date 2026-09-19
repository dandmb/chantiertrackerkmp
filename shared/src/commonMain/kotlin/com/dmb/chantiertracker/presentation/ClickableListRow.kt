package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.main.ChevronRightIcon

/**
 * A bordered, clickable row for the "sub-list inside a detail screen" tier —
 * shared so every navigable list looks the same: M3 press/ripple from
 * `Surface(onClick)`, a trailing chevron, identical padding and border.
 * Used by the stages list (project detail) and the daily-logs list (stage
 * detail). The top-level projects list uses `ProjectCard` (elevated `Card`)
 * on purpose — a more prominent tier.
 *
 * [content] is laid out in a [Row] before the chevron; give the growing
 * element `Modifier.weight(1f)` so the chevron stays pinned to the right.
 */
@Composable
fun ClickableListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Icon(
                imageVector = ChevronRightIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
