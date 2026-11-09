package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val TRANSITION_MILLIS = 250
private const val SLIDE_FRACTION = 0.12f

class NavigationTransitions(
    val durationMillis: Int,
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
) {
    companion object {
        private val none = NavigationTransitions(
            durationMillis = 0,
            enter = fadeIn(snap()),
            exit = fadeOut(snap()),
            popEnter = fadeIn(snap()),
            popExit = fadeOut(snap()),
        )

        fun of(reduceMotion: Boolean): NavigationTransitions {
            if (reduceMotion) return none
            val slide = tween<androidx.compose.ui.unit.IntOffset>(TRANSITION_MILLIS, easing = FastOutSlowInEasing)
            val fade = tween<Float>(TRANSITION_MILLIS, easing = FastOutSlowInEasing)
            fun shift(width: Int) = (width * SLIDE_FRACTION).toInt()
            return NavigationTransitions(
                durationMillis = TRANSITION_MILLIS,
                enter = slideInHorizontally(slide) { shift(it) } + fadeIn(fade),
                exit = slideOutHorizontally(slide) { -shift(it) } + fadeOut(fade),
                popEnter = slideInHorizontally(slide) { -shift(it) } + fadeIn(fade),
                popExit = slideOutHorizontally(slide) { shift(it) } + fadeOut(fade),
            )
        }
    }
}

@Composable
fun rememberNavigationTransitions(): NavigationTransitions {
    val reduceMotion = rememberReduceMotion()
    return remember(reduceMotion) { NavigationTransitions.of(reduceMotion) }
}

interface ReduceMotionSetting {
    fun isOn(): Boolean
    fun watch(onChange: () -> Unit): () -> Unit
}

@Composable
fun rememberReduceMotion(): Boolean = rememberReduceMotion(rememberReduceMotionSetting())

@Composable
fun rememberReduceMotion(setting: ReduceMotionSetting): Boolean {
    var on by remember(setting) { mutableStateOf(setting.isOn()) }
    DisposableEffect(setting) {
        val stop = setting.watch { on = setting.isOn() }
        on = setting.isOn()
        onDispose { stop() }
    }
    return on
}

@Composable
expect fun rememberReduceMotionSetting(): ReduceMotionSetting

@Composable
expect fun rememberExitApp(): () -> Unit
