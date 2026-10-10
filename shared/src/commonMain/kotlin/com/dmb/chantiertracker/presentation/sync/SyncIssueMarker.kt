package com.dmb.chantiertracker.presentation.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.presentation.main.SyncDeletedOnServerIcon
import com.dmb.chantiertracker.presentation.main.SyncRefusedIcon
import com.dmb.chantiertracker.presentation.main.SyncWaitingIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.sync_marker_open
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class SyncIssueMarkers(items: List<SyncIssueItem> = emptyList(), val onOpen: (SyncIssueItem) -> Unit = {}) {
    private val byKey = items.associateBy { it.key }

    fun of(target: SyncIssueTarget, localId: String): SyncIssueItem? = byKey["$target:$localId"]
}

val LocalSyncIssueMarkers = compositionLocalOf { SyncIssueMarkers() }

enum class SyncIssueMarkerStyle { LABELLED, ICON_ONLY }

fun syncIssueMarkerTag(target: SyncIssueTarget, localId: String): String = "sync-marker:$target:$localId"

fun SyncIssueKind.markerIcon(): ImageVector = when (this) {
    SyncIssueKind.REFUSED, SyncIssueKind.UPDATE_REFUSED, SyncIssueKind.DELETE_REFUSED -> SyncRefusedIcon
    SyncIssueKind.DELETED_ON_SERVER -> SyncDeletedOnServerIcon
    SyncIssueKind.BLOCKED_BY_PARENT -> SyncWaitingIcon
}

@Composable
fun SyncIssueMarkersProvider(
    onOpen: (SyncIssueItem) -> Unit,
    viewModel: SyncIssueMarkersViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val markers = remember(items, onOpen) { SyncIssueMarkers(items, onOpen) }
    CompositionLocalProvider(LocalSyncIssueMarkers provides markers, content = content)
}

@Composable
fun SyncIssueMarker(
    target: SyncIssueTarget,
    localId: String,
    modifier: Modifier = Modifier,
    style: SyncIssueMarkerStyle = SyncIssueMarkerStyle.LABELLED,
) {
    val markers = LocalSyncIssueMarkers.current
    val item = markers.of(target, localId) ?: return
    val kind = item.issue.kind
    val label = stringResource(kind.statusRes())
    val colors = MaterialTheme.colorScheme
    val container = when (kind) {
        SyncIssueKind.BLOCKED_BY_PARENT -> colors.secondaryContainer
        SyncIssueKind.DELETED_ON_SERVER -> colors.surfaceVariant
        else -> colors.errorContainer
    }
    val content = when (kind) {
        SyncIssueKind.BLOCKED_BY_PARENT -> colors.onSecondaryContainer
        SyncIssueKind.DELETED_ON_SERVER -> colors.onSurfaceVariant
        else -> colors.onErrorContainer
    }
    val icon = kind.markerIcon()
    val iconModifier = Modifier.size(with(LocalDensity.current) { 16.sp.toDp() }).testTag("sync-marker-icon:${icon.name}")
    Box(
        modifier = modifier
            .testTag(syncIssueMarkerTag(target, localId))
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(Res.string.sync_marker_open), role = Role.Button) { markers.onOpen(item) },
        contentAlignment = if (style == SyncIssueMarkerStyle.LABELLED) Alignment.CenterStart else Alignment.Center,
    ) {
        Surface(
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.small,
            border = if (kind == SyncIssueKind.DELETED_ON_SERVER) BorderStroke(1.dp, colors.outline) else null,
        ) {
            when (style) {
                SyncIssueMarkerStyle.LABELLED -> Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, modifier = iconModifier)
                    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false))
                }
                SyncIssueMarkerStyle.ICON_ONLY -> Icon(icon, contentDescription = label, modifier = Modifier.padding(4.dp).then(iconModifier))
            }
        }
    }
}
