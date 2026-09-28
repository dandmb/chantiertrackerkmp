package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.video_desktop_open
import com.dmb.chantiertracker.resources.video_desktop_unavailable
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File

// No bundled player on Desktop (VLCJ would drag in a native runtime, and
// Desktop isn't a field-use target for watching site videos — ADR-36).
// Hand the file to whatever the OS uses for video.
@Composable
actual fun VideoPlayer(localPath: String, modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.video_desktop_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(File(localPath)) } }) {
            Text(stringResource(Res.string.video_desktop_open))
        }
    }
}
