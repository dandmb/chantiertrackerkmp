package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmb.chantiertracker.presentation.main.WarningIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.staging_banner
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps the whole app. On the pre-production distribution build ([showStagingBanner]),
 * a permanent yellow strip sits above every screen so a tester never mistakes this
 * build for a real production install — it runs against the production backend
 * with real data (see MOBILE_CONTEXT §14 / ADR-24).
 */
@Composable
fun AppChrome(showStagingBanner: Boolean, content: @Composable () -> Unit) {
    if (!showStagingBanner) {
        content()
        return
    }
    Column(Modifier.fillMaxSize()) {
        StagingBanner()
        // The strip already sits under the status bar, so the screens below must
        // not re-apply that inset (their top bars would double-pad otherwise).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.statusBars),
        ) {
            content()
        }
    }
}

@Composable
private fun StagingBanner() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag(STAGING_BANNER_TAG)) {
        Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WarningIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                CapitalTitle(
                    text = stringResource(Res.string.staging_banner),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 19.sp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.onPrimaryContainer))
    }
}

const val STAGING_BANNER_TAG = "staging-banner"
