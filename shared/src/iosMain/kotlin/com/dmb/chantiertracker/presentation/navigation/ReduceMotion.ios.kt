package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

private object IosReduceMotionSetting : ReduceMotionSetting {
    override fun isOn(): Boolean = UIAccessibilityIsReduceMotionEnabled()

    override fun watch(onChange: () -> Unit): () -> Unit {
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> onChange() }
        return { NSNotificationCenter.defaultCenter.removeObserver(token) }
    }
}

@Composable
actual fun rememberReduceMotionSetting(): ReduceMotionSetting = IosReduceMotionSetting

@Composable
actual fun rememberExitApp(): () -> Unit = remember { {} }
