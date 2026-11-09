package com.dmb.chantiertracker.presentation.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.session.PendingWriteCounter
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.presentation.main.CloudOffIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.offline_banner_body
import com.dmb.chantiertracker.resources.offline_banner_pending
import com.dmb.chantiertracker.resources.offline_banner_title
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

data class OfflineBannerState(val offline: Boolean = false, val pending: Int = 0)

class OfflineBannerViewModel(connectivity: ConnectivityObserver, pendingWrites: PendingWriteCounter) : ViewModel() {
    val state: StateFlow<OfflineBannerState> =
        combine(connectivity.online, pendingWrites.observePending()) { online, pending -> OfflineBannerState(!online, pending) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineBannerState(offline = !connectivity.online.value))
}

@Composable
fun TopBarWithOfflineBanner(state: OfflineBannerState, topBar: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        topBar()
        if (state.offline) OfflineBanner(state.pending)
    }
}

@Composable
private fun OfflineBanner(pending: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CloudOffIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.offline_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (pending > 0) pluralStringResource(Res.plurals.offline_banner_pending, pending, pending)
                    else stringResource(Res.string.offline_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
