package com.dmb.chantiertracker.presentation.logs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays a locally-stored MP4 with the platform's own player (ADR-36):
 * Android → Media3 `ExoPlayer` + `PlayerView`; iOS → `AVPlayerViewController`;
 * Desktop → a fallback that hands the file to the system player (no bundled
 * player — Desktop is not a field-use target for watching site videos).
 * `localPath` is a `FileKit.filesDir` path, always a real file by the time a
 * video is displayed (it was downloaded transcoded, ADR-35).
 */
@Composable
expect fun VideoPlayer(localPath: String, modifier: Modifier)
