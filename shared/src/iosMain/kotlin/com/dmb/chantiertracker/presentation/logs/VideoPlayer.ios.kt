@file:OptIn(ExperimentalForeignApi::class)

package com.dmb.chantiertracker.presentation.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@Composable
actual fun VideoPlayer(localPath: String, modifier: Modifier) {
    val controller = remember(localPath) {
        AVPlayerViewController().apply {
            player = AVPlayer(uRL = fileUrl(localPath))
            // Fit the video to the (full-screen) container keeping its aspect
            // ratio — never a fixed strip (ADR-40). This is AVKit's default;
            // set explicitly so it survives a container that fills the screen.
            videoGravity = AVLayerVideoGravityResizeAspect
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.player?.pause() }
    }
    UIKitViewController(
        factory = { controller },
        modifier = modifier,
    )
}

// `localPath` is normally a plain filesystem path (resolved from the stable key,
// ADR-41). Be defensive about a legacy "file://…" URL string too: NSURL.fileURLWithPath
// would treat it as a path and double-wrap it, and AVPlayer would never find the file.
private fun fileUrl(localPath: String): NSURL =
    if (localPath.startsWith("file:")) {
        NSURL(string = localPath) ?: NSURL.fileURLWithPath(localPath)
    } else {
        NSURL.fileURLWithPath(localPath)
    }
