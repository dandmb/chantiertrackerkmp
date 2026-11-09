package com.dmb.chantiertracker.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.navigation.NavigationTransitions
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class ReducedMotionNoOverlapUiTest {

    private fun screen(name: String) = when (name) {
        "list" -> Color.Red
        else -> Color.Blue
    }

    @Test
    fun with_animations_removed_two_screens_are_never_drawn_on_top_of_each_other() = runComposeUiTest {
        val transitions = NavigationTransitions.of(reduceMotion = true)
        var target by mutableStateOf("list")
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(200.dp, 100.dp).background(Color.White)) {
                AnimatedContent(target, transitionSpec = { transitions.enter togetherWith transitions.exit }) { name ->
                    Box(Modifier.fillMaxSize()) {
                        val alignment = if (name == "list") Alignment.CenterStart else Alignment.CenterEnd
                        Box(Modifier.align(alignment).padding(10.dp).size(60.dp).background(screen(name)))
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()

        for (next in listOf("detail", "list")) {
            target = next
            repeat(4) { frame ->
                mainClock.advanceTimeByFrame()
                val pixels = onRoot().captureToImage().toPixelMap()
                val listShown = pixels[40, 50] == Color.Red
                val detailShown = pixels[160, 50] == Color.Blue
                assertFalse(listShown && detailShown, "frame $frame towards $next shows both screens")
            }
        }
    }
}
