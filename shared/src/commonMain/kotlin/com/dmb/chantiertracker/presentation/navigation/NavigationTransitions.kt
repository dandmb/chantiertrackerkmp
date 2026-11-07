package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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
        private val none = NavigationTransitions(0, EnterTransition.None, ExitTransition.None, EnterTransition.None, ExitTransition.None)

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

@Composable
expect fun rememberReduceMotion(): Boolean

@Composable
expect fun rememberExitApp(): () -> Unit
