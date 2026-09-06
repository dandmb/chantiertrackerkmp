@file:OptIn(ExperimentalForeignApi::class)

package com.dmb.chantiertracker.presentation.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@Composable
actual fun VideoPlayer(localPath: String, modifier: Modifier) {
    val controller = remember(localPath) {
        AVPlayerViewController().apply {
            player = AVPlayer(uRL = NSURL.fileURLWithPath(localPath))
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
