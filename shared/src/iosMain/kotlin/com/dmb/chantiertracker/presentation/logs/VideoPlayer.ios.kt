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
            player = AVPlayer(uRL = NSURL.fileURLWithPath(localPath))
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
