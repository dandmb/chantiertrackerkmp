package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmb.chantiertracker.presentation.theme.WarningAmber
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.staging_banner
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps the whole app. On the pre-production distribution build ([showStagingBanner]),
 * a permanent amber strip sits above every screen so a tester never mistakes this
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
    Surface(color = WarningAmber, contentColor = Color.White) {
        Text(
            text = stringResource(Res.string.staging_banner),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = 3.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
