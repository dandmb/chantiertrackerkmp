package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
fun <T> TopBarFollowingContent(target: T, transitions: NavigationTransitions, content: @Composable (T) -> Unit) {
    if (transitions.durationMillis != 0) {
        content(target)
        return
    }
    val state = remember { SeekableTransitionState(target) }
    LaunchedEffect(target) { state.animateTo(target) }
    rememberTransition(state, label = "topBar").AnimatedContent(
        transitionSpec = { transitions.enter togetherWith transitions.exit },
    ) { shown -> content(shown) }
}
